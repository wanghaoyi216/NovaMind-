package com.novamind.pay.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.alipay.easysdk.factory.Factory;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novamind.common.autoconfigure.mq.RabbitMqHelper;
import com.novamind.common.autoconfigure.redisson.annotations.Lock;
import com.novamind.common.constants.MqConstants;
import com.novamind.common.exceptions.BadRequestException;
import com.novamind.common.exceptions.BizIllegalException;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.utils.DateUtils;
import com.novamind.common.utils.JsonUtils;
import com.novamind.common.utils.StringUtils;
import com.novamind.pay.constants.IdempotencyConstants;
import com.novamind.pay.domain.po.IdempotencyRecord;
import com.novamind.pay.domain.po.PayOrder;
import com.novamind.pay.domain.po.RefundOrder;
import com.novamind.pay.mapper.IdempotencyRecordMapper;
import com.novamind.pay.sdk.constants.PayConstants;
import com.novamind.pay.sdk.constants.PayErrorInfo;
import com.novamind.pay.sdk.dto.PayResultDTO;
import com.novamind.pay.sdk.dto.RefundResultDTO;
import com.novamind.pay.service.INotifyService;
import com.novamind.pay.service.IPayOrderService;
import com.novamind.pay.service.IRefundOrderService;
import com.novamind.pay.third.ali.AliPayService;
import com.novamind.pay.third.model.RefundStatus;
import com.novamind.pay.third.wx.config.WxPayProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.exception.ParseException;
import com.wechat.pay.contrib.apache.httpclient.exception.ValidationException;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements INotifyService {

    private final CertificatesManager certificatesManager;
    private final WxPayProperties properties;
    private final IPayOrderService payOrderService;
    private final RabbitMqHelper rabbitMqHelper;
    private final IRefundOrderService refundOrderService;
    private final IdempotencyRecordMapper idempotencyRecordMapper;

    @Override
    // R6 安全修复：分布式锁移到 public 入口。原 @Lock(PAY_NOTIFY) 打在 private
    // checkNotifyData 上且经 this. 内部调用——Spring AOP 代理对 private 方法和
    // 自调用均不拦截，该锁自上线以来从未生效过（并发回调可同时通过幂等检查）。
    // 移到入口后锁覆盖"验签+解析+幂等校验+状态更新"全流程；全局单锁串行化
    // 支付回调是可接受的（支付回调 QPS 极低，正确性远优先于吞吐）。
    @Lock(name = PayConstants.RedisKeyFormatter.PAY_NOTIFY)
    public void handleWxPayNotify(NotificationRequest request) {
        log.info("收到微信支付通知：{}", request.getBody());
        // 1.尝试校验微信通知请求参数，安全校验
        Notification notification = checkWxNotifyRequest(request);
        if (notification == null || !StringUtils.equals(notification.getEventType(), "TRANSACTION.SUCCESS")) {
            // 通知类型不是支付成功，不做处理
            return;
        }
        // 2.解析回调加密的数据
        String decryptData = notification.getDecryptData();
        JSONObject data = JsonUtils.parseObj(decryptData);

        // 3.获取用于业务校验的基本信息
        // 3.1.交易单号
        Long tradingOrderNo = data.getLong("out_trade_no");
        // 3.2.订单金额
        JSONObject amountObject = data.getJSONObject("amount");
        Integer amount = amountObject == null ? null : amountObject.getInt("total");
        // 3.3.订单支付时间
        LocalDateTime successTime = data.getLocalDateTime("success_time", LocalDateTime.now());

        // 4.P0 改造：表级幂等检查（验签之后、业务处理之前），重复通知直接返回
        if (tradingOrderNo != null && duplicatedPayNotify(tradingOrderNo)) return;

        // 5.校验通知数据，主要是业务校验、幂等校验
        PayOrder payOrder = checkNotifyData(tradingOrderNo, amount, successTime);
        if (payOrder == null) return;

        // 6.通知业务服务
        rabbitMqHelper.send(
                MqConstants.Exchange.PAY_EXCHANGE,
                MqConstants.Key.PAY_SUCCESS,
                PayResultDTO.builder()
                        .payChannel(payOrder.getPayChannelCode())
                        .payOrderNo(payOrder.getPayOrderNo())
                        .bizOrderId(payOrder.getBizOrderNo())
                        .successTime(successTime)
                        .build());

        // 7.P0 改造：业务处理成功，幂等记录置为 SUCCESS 并记录回写给渠道的响应体
        markPayNotifySuccess(tradingOrderNo, payOrder.getBizOrderNo(), "{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
    }


    @Override
    public void handleWxPayRefundNotify(NotificationRequest request) {
        log.info("收到微信退款通知：{}", request.getBody());
        // 1.尝试校验微信通知请求参数，安全校验
        Notification notification = checkWxNotifyRequest(request);
        if (notification == null || !StringUtils.equalsAny(
                notification.getEventType(), "REFUND.SUCCESS", "REFUND.ABNORMAL", "REFUND.CLOSED")) {
            // 通知类型错误，直接返回
            log.debug("微信退款通知类型异常");
            return;
        }

        // 2.解析通知数据
        String decryptData = notification.getDecryptData();
        JSONObject data = JsonUtils.parseObj(decryptData);
        // 2.1.退款单号
        Long refundOrderNo = data.getLong("out_refund_no");
        if (refundOrderNo == null) {
            log.error("微信通知数据有误，缺少退款单号");
            throw new BadRequestException("微信通知数据有误，缺少退款单号");
        }
        // 2.2.退款状态
        String statusStr = notification.getEventType();
        RefundStatus status = handleWxRefundStatus(statusStr);

        // 3.幂等性校验
        RefundOrder refundOrder = checkRefundData(refundOrderNo, status, null);
        if (refundOrder == null) return;

        // 4.发送MQ通知业务端
        rabbitMqHelper.send(
                MqConstants.Exchange.PAY_EXCHANGE,
                MqConstants.Key.REFUND_CHANGE,
                RefundResultDTO.builder()
                        .status(status == RefundStatus.SUCCESS ? RefundResultDTO.SUCCESS : RefundResultDTO.FAILED)
                        .bizPayOrderId(refundOrder.getBizOrderNo())
                        .bizRefundOrderId(refundOrder.getBizRefundOrderNo())
                        .refundChannel(refundOrder.getRefundChannel())
                        .refundOrderNo(refundOrder.getRefundOrderNo())
                        .msg(data.getStr("msg"))
                        .build()
        );
    }

    @Override
    // R6 安全修复：同 handleWxPayNotify——锁必须在 public 入口才能被 AOP 拦截。
    @Lock(name = PayConstants.RedisKeyFormatter.PAY_NOTIFY)
    public void handleAliPayNotify(Map<String, String> request) {
        log.error("收到阿里支付通知信息，request = {}", request);
        // 1.判断是否是成功通知
        String tradeStatus = request.get("trade_status");
        if (!StrUtil.equals(tradeStatus, "TRADE_SUCCESS")) {
            // 通知结果不是成功，直接结束
            return;
        }
        // 2.验签
        checkAliNotifyRequest(request);

        // 3.获取用于业务校验的基本信息
        // 3.1.交易单号
        String out_trade_no = request.get("out_trade_no");
        Long tradingOrderNo = StringUtils.isNumeric(out_trade_no) ? Long.valueOf(out_trade_no) : null;
        // 3.2.订单金额，阿里返回的订单金额要乘100
        String total_amount = request.get("total_amount");
        Integer amount = StringUtils.isNotBlank(total_amount) ? AliPayService.transferStringAmount2Int(total_amount) : null;
        // 3.3.订单支付时间
        String success_time = request.get("notify_time");
        LocalDateTime successTime = StringUtils.isBlank(success_time) ?
                LocalDateTime.now() : DateUtils.parse(success_time, DateUtils.DEFAULT_DATE_TIME_FORMAT);

        // 4.P0 改造：表级幂等检查（验签之后、业务处理之前），重复通知直接返回
        if (tradingOrderNo != null && duplicatedPayNotify(tradingOrderNo)) return;

        // 5.校验通知数据，主要是业务校验、幂等校验
        PayOrder payOrder = checkNotifyData(tradingOrderNo, amount, successTime);
        if (payOrder == null) return;

        // 6.通知业务服务
        rabbitMqHelper.send(
                MqConstants.Exchange.PAY_EXCHANGE,
                MqConstants.Key.PAY_SUCCESS,
                PayResultDTO.builder()
                        .payOrderNo(payOrder.getPayOrderNo())
                        .payChannel(payOrder.getPayChannelCode())
                        .bizOrderId(payOrder.getBizOrderNo())
                        .successTime(successTime)
                        .build()
        );

        // 7.P0 改造：业务处理成功，幂等记录置为 SUCCESS 并记录回写给渠道的响应体
        markPayNotifySuccess(tradingOrderNo, payOrder.getBizOrderNo(), "success");
    }


    /**
     * <h2>P0 改造：支付通知表级幂等（接入 idempotency_record 表）</h2>
     * <p>调用时机：验签之后、业务处理之前。</p>
     * <ol>
     *   <li>SELECT 命中且 status=SUCCESS → 已处理过，直接判重返回；</li>
     *   <li>未命中 → INSERT 一条 PENDING 占位，捕获 {@link DuplicateKeyException}
     *       （uk_biz_key 唯一键冲突）视为并发重复通知返回；</li>
     *   <li>命中但非 SUCCESS（PENDING/FAILED）→ 上次未完成，放行重入
     *       （业务侧仍有 pay_order 乐观锁兜底）。</li>
     * </ol>
     *
     * @param tradingOrderNo 交易单号（out_trade_no）
     * @return true = 本次通知是重复通知，直接返回
     */
    private boolean duplicatedPayNotify(Long tradingOrderNo) {
        String idemKey = String.valueOf(tradingOrderNo);
        // 1.先查：命中且已 SUCCESS => 直接判重
        IdempotencyRecord record = idempotencyRecordMapper.selectOne(Wrappers.<IdempotencyRecord>lambdaQuery()
                .eq(IdempotencyRecord::getBizType, IdempotencyConstants.IDEM_BIZ_TYPE_PAY_NOTIFY)
                .eq(IdempotencyRecord::getIdemKey, idemKey));
        if (record != null && record.getStatus() != null && record.getStatus() == IdempotencyConstants.IDEM_STATUS_SUCCESS) {
            log.info("支付回调重复通知（幂等表命中 SUCCESS），直接忽略：{}", tradingOrderNo);
            return true;
        }
        // 2.未命中 => 插入 PENDING 占位
        if (record == null) {
            try {
                idempotencyRecordMapper.insert(new IdempotencyRecord()
                        .setBizType(IdempotencyConstants.IDEM_BIZ_TYPE_PAY_NOTIFY)
                        .setIdemKey(idemKey)
                        .setStatus(IdempotencyConstants.IDEM_STATUS_PENDING)
                        .setCreateTime(LocalDateTime.now())
                        .setUpdateTime(LocalDateTime.now()));
                return false;
            } catch (DuplicateKeyException e) {
                // 并发重复通知：另一线程已插入同 (biz_type, idem_key) 记录
                log.warn("支付回调并发重复通知，幂等键冲突：{}", tradingOrderNo);
                return true;
            }
        }
        // 3.命中但非 SUCCESS：上次处理未完成，放行重入
        return false;
    }

    /**
     * P0 改造：支付通知业务成功后，把幂等记录翻转为 SUCCESS 并回写渠道响应。
     */
    private void markPayNotifySuccess(Long tradingOrderNo, Long bizOrderNo, String responseToChannel) {
        idempotencyRecordMapper.update(null, Wrappers.<IdempotencyRecord>lambdaUpdate()
                .set(IdempotencyRecord::getStatus, IdempotencyConstants.IDEM_STATUS_SUCCESS)
                .set(bizOrderNo != null, IdempotencyRecord::getBizId, String.valueOf(bizOrderNo))
                .set(StringUtils.isNotBlank(responseToChannel), IdempotencyRecord::getResponseToChannel, responseToChannel)
                .eq(IdempotencyRecord::getBizType, IdempotencyConstants.IDEM_BIZ_TYPE_PAY_NOTIFY)
                .eq(IdempotencyRecord::getIdemKey, String.valueOf(tradingOrderNo)));
    }


    /**
     * 校验退款通知数据。<br>
     * P0 说明：退款回调<b>不接入</b> idempotency_record 表——本方法已有退款单状态机幂等
     * （status 未变化判重 + eq(旧状态) 条件更新的乐观锁语义），天然防重复处理；
     * 再叠加一张表属于过度设计，故保持现状。
     */
    private RefundOrder checkRefundData(Long refundOrderNo, RefundStatus status, String channel) {
        // 1.查询退款单
        RefundOrder refundOrder = refundOrderService.queryByRefundOrderNo(refundOrderNo);
        // 2.判断是否为空
        if (refundOrder == null) {
            throw new BadRequestException("通知数据有误，退款单不存在");
        }
        // 3.判断状态是否变更
        if (status.equalsValue(refundOrder.getStatus())) {
            // 订单状态没有变化，属于重复通知
            return null;
        }
        // 4.更新退款单状态
        boolean success = refundOrderService.lambdaUpdate()
                .set(RefundOrder::getStatus, status.getValue())
                .set(StringUtils.isNotBlank(channel), RefundOrder::getRefundChannel, channel)
                .eq(RefundOrder::getId, refundOrder.getId())
                .eq(RefundOrder::getStatus, refundOrder.getStatus())
                .update();
        if(!success){
            return null;
        }
        return refundOrder;
    }

    private RefundStatus handleWxRefundStatus(String statusStr) {
        if (StringUtils.equalsAny(statusStr, "REFUND.CLOSED", "REFUND.ABNORMAL")) {
            return RefundStatus.FAILED;
        }
        if ("REFUND.SUCCESS".equals(statusStr)) {
            return RefundStatus.SUCCESS;
        }
        return RefundStatus.UN_KNOWN;
    }


    private void checkAliNotifyRequest(Map<String, String> request) {
        try {
            Boolean isValid = Factory.Payment.Common().verifyNotify(request);
            if (!isValid) {
                // 通知签名有误
                log.error("阿里支付通知回调验签失败，request = {}", request);
                throw new BadRequestException(PayErrorInfo.INVALID_NOTIFY_PARAM);
            }
        } catch (Exception e) {
            log.error("获取阿里验签工具异常", e);
            throw new CommonException("获取阿里验签工具异常", e);
        }
    }


    @Nullable
    private Notification checkWxNotifyRequest(NotificationRequest request) {
        try {
            Verifier verifier = certificatesManager.getVerifier(properties.getMchId());
            String apiV3Key = properties.getApiV3Key();
            NotificationHandler handler = new NotificationHandler(verifier, apiV3Key.getBytes(StandardCharsets.UTF_8));
            // 验签和解析请求体
            return handler.parse(request);

        } catch (NotFoundException e) {
            log.error("找不到商户{}的校验证书", properties.getMchId(), e);
            return null;
        } catch (ValidationException e) {
            log.error("微信回调结果校验失败", e);
            throw new BadRequestException(400, "微信回调结果校验失败", e);
        } catch (ParseException e) {
            log.error("微信回调结果解析失败", e);
            throw new BadRequestException(400, "微信回调结果解析失败", e);
        } catch (RuntimeException e) {
            log.error("微信回调结果处理失败", e);
            throw new BadRequestException(400, "微信回调结果处理失败", e);
        }
    }

    @Nullable
    // R6：原 @Lock 已上移到 public 入口（private + this.自调用 AOP 不生效，属死注解）。
    private PayOrder checkNotifyData(Long tradingOrderNo, Integer amount, LocalDateTime successTime) {
        // 1.数据非空校验
        if (tradingOrderNo == null || amount == null) {
            throw new BadRequestException(400, PayErrorInfo.INVALID_NOTIFY_PARAM);
        }
        log.info("支付回调通知：payOrderNo = {},  amount = {}", tradingOrderNo, amount);

        // 2.查询交易单，幂等校验
        PayOrder payOrder = payOrderService.queryByPayOrderNo(tradingOrderNo);
        // 2.1.非空校验
        if (payOrder == null) {
            log.error("支付回调通知的支付单{}不存在", tradingOrderNo);
            return null;
        }
        // 2.2.支付单如果是已支付或已关闭，则不能重复处理
        if (payOrder.success() || payOrder.closed()) {
            log.error("支付回调通知的支付单{}已经支付或已经关闭，属于重复通知", tradingOrderNo);
            return null;
        }

        // 3.校验支付金额
        if (!payOrder.getAmount().equals(amount)) {
            // 金额有误
            log.error("支付回调通知的金额有误，支付单号：{}，通知金额：{}， 实际金额：{}",
                    tradingOrderNo, amount, payOrder.getAmount());
            throw new BizIllegalException("微信通知的金额有误");
        }

        // 4.更新订单状态，同时基于乐观锁做幂等处理
        boolean success = payOrderService.markPayOrderSuccess(payOrder.getId(), successTime);
        if (!success) {
            // 如果更新失败，说明是重复通知
            return null;
        }

        return payOrder;
    }

}
