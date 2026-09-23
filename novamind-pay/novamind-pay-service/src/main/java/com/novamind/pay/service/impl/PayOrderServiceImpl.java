package com.novamind.pay.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novamind.common.autoconfigure.mq.RabbitMqHelper;
import com.novamind.common.autoconfigure.redisson.annotations.Lock;
import com.novamind.common.autoconfigure.redisson.enums.LockStrategy;
import com.novamind.common.cache.CacheAsideTemplate;
import com.novamind.common.constants.MqConstants;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.common.exceptions.BadRequestException;
import com.novamind.common.exceptions.BizIllegalException;
import com.novamind.common.hotkey.HotKeyGuard;
import com.novamind.common.utils.BeanUtils;
import com.novamind.common.utils.StringUtils;
import com.novamind.pay.constants.NotifyStatus;
import com.novamind.pay.domain.po.PayOrder;
import com.novamind.pay.mapper.PayOrderMapper;
import com.novamind.pay.sdk.constants.PayConstants;
import com.novamind.pay.sdk.constants.PayErrorInfo;
import com.novamind.pay.sdk.dto.PayApplyDTO;
import com.novamind.pay.sdk.dto.PayResultDTO;
import com.novamind.pay.service.IPayOrderService;
import com.novamind.pay.third.IPayService;
import com.novamind.pay.third.model.PayStatus;
import com.novamind.pay.third.model.PayStatusResponse;
import com.novamind.pay.third.model.PrepayResponse;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static com.novamind.pay.sdk.constants.PayErrorInfo.*;

/**
 * <p>
 * 支付订单 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    @Resource
    private Map<String, IPayService> payServiceChannels;
    private final RabbitMqHelper rabbitMqHelper;

    /**
     * R23 Cache-Aside 统一封装：替代 @Cacheable，跨实例共享缓存。
     * bizOrderNo 是典型的"多实例都会读、且会被通知/对账高频访问"key，
     * 用 CacheAsideTemplate 同时拿到 ① 跨实例一致性 ② 防击穿 ③ 防穿透 ④ 防雪崩。
     */
    private final CacheAsideTemplate cacheAside;

    /**
     * R9 Hot Key 防护：{@code pay:order:{bizOrderNo}} 是典型热读路径（同一笔订单在支付
     * 有效期内会被前端/通知/对账等多端反复查询）。原本每次都打 MySQL，
     * 现在通过 {@link HotKeyGuard} 在 JVM 内吸收 99% 的请求，分散到 16 个独立桶。
     */
    private final HotKeyGuard<Long, PayOrder> bizOrderGuard =
            HotKeyGuard.<Long, PayOrder>builder()
                    .name("pay:order")
                    .maxSizePerBucket(5_000)
                    .expireAfterWrite(java.time.Duration.ofMinutes(5))
                    .allowNull(false)
                    .build();

    /**
     * R9 Hot Key 防护：{@code pay:order:{payOrderNo}} 在通知/退款路径下也会被高频读取。
     */
    private final HotKeyGuard<Long, PayOrder> payOrderGuard =
            HotKeyGuard.<Long, PayOrder>builder()
                    .name("pay:payOrder")
                    .maxSizePerBucket(5_000)
                    .expireAfterWrite(java.time.Duration.ofMinutes(5))
                    .allowNull(false)
                    .build();

    @Override
    @Lock(name = PayConstants.RedisKeyFormatter.PAY_APPLY, leaseTime = 3, autoUnlock = false)
    public String applyPayOrder(PayApplyDTO payApplyDTO) {
        log.debug("准备创建支付单，业务订单号：{}", payApplyDTO.getBizOrderNo());
        // 1.选择支付渠道
        IPayService payService = payServiceChannels.get(payApplyDTO.getPayChannelCode());
        if (payService == null) {
            log.error("用户选择的支付渠道不存在，业务单号：{}", payApplyDTO.getBizOrderNo());
            throw new BadRequestException(INVALID_PAY_CHANNEL);
        }

        // 2.幂等性校验
        PayOrder payOrder = checkIdempotent(payApplyDTO);
        if (StringUtils.isNotBlank(payOrder.getQrCodeUrl())) {
            log.debug("支付链接已经存在，不再重新创建，直接返回");
            return payOrder.getQrCodeUrl();
        }

        // 3.需要生成新支付链接，调用第三方，完成支付单下单
        PrepayResponse prepayResponse = payService.createPrepayOrder(
                payApplyDTO.getOrderInfo(), payOrder.getPayOrderNo().toString(), payOrder.getAmount());

        // 4.更新支付链接等信息到数据库
        updatePayResult2DB(prepayResponse, payOrder.getId());

        if (!prepayResponse.isSuccess()) {
            log.error("预创建支付单失败，详情：{}", prepayResponse.getDetail());
            throw new BizIllegalException(PayErrorInfo.CREATE_PAY_ORDER_FAILED);
        }
        // 5.返回支付链接
        log.debug("支付单生成成功，返回支付链接：{}", prepayResponse.getPayUrl());
        return prepayResponse.getPayUrl();
    }

    private void updatePayResult2DB(PrepayResponse prepayResponse, Long payOrderId) {
        try {
            lambdaUpdate()
                    .set(prepayResponse.isSuccess(), PayOrder::getQrCodeUrl, prepayResponse.getPayUrl())
                    .set(prepayResponse.isSuccess(), PayOrder::getStatus, PayStatus.WAIT_BUYER_PAY.getValue())
                    .set(!prepayResponse.isSuccess(), PayOrder::getResultCode, prepayResponse.getCode())
                    .set(!prepayResponse.isSuccess(), PayOrder::getResultMsg, prepayResponse.getMsg())
                    .eq(PayOrder::getId, payOrderId)
                    .update();
        } catch (Exception e) {
            log.error("更新支付单结果到数据时发生异常", e);
        }
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        PayOrder payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setNotifyTimes(0);
        payOrder.setNotifyStatus(NotifyStatus.UN_CALL.getValue());
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.NOT_COMMIT.getValue());
        return payOrder;
    }

    private PayOrder checkIdempotent(PayApplyDTO payApplyDTO) {
        // 1.首先查询支付订单
        PayOrder oldOrder = queryByBizOrderNo(payApplyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(payApplyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            return payOrder;
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException(PAY_ORDER_ALREADY_PAY_CODE, PAY_ORDER_ALREADY_PAY);
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException(PAY_ORDER_ALREADY_CLOSE_CODE, PAY_ORDER_ALREADY_CLOSE);
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), payApplyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            PayOrder payOrder = buildPayOrder(payApplyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // 6.旧单已经存在，且可能是未支付或未提交，且支付渠道一致，直接返回旧数据
        return oldOrder;
    }

    @Override
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        // R23 案例 1：CacheAsideTemplate（分布式版），
        // 与 queryByPayOrderNo 的 HotKeyGuard（JVM 本地版）形成对比：
        // HotKeyGuard 只在单实例内吸收热读；CacheAsideTemplate 走 Redis，
        // 多实例共享缓存 + 防击穿 + 防穿透 + 防雪崩。
        return cacheAside.get("pay:order:biz:" + bizOrderNo, PayOrder.class,
                () -> lambdaQuery().eq(PayOrder::getBizOrderNo, bizOrderNo).one(),
                Duration.ofMinutes(5));
    }

    /**
     * R23 案例 1 配套：写 DB 成功后删缓存（Cache-Aside 标准写路径）。
     * 拆方法以便 {@link #markPayOrderSuccess(Long, LocalDateTime)} 复用。
     */
    private void evictBizOrderCache(Long bizOrderNo) {
        if (bizOrderNo == null) return;
        cacheAside.invalidate("pay:order:biz:" + bizOrderNo);
    }

    @Override
    public PayResultDTO queryPayResult(Long bizOrderNo) {
        // 1.查询支付单
        PayOrder payOrder = queryByBizOrderNo(bizOrderNo);
        if (payOrder == null) {
            throw new BizIllegalException(PAY_ORDER_NOT_FOUND);
        }
        // 2.判断支付状态
        if (payOrder.success()) {
            // 2.1.支付成功
            return PayResultDTO.builder()
                    .payOrderNo(payOrder.getPayOrderNo())
                    .successTime(payOrder.getPaySuccessTime())
                    .payChannel(payOrder.getPayChannelCode())
                    .build();
        }
        // 2.2.未支付
        if (payOrder.notCommit() || payOrder.waitBuyerPay()) {
            return PayResultDTO.builder()
                    .status(PayStatus.WAIT_BUYER_PAY.getValue())
                    .build();
        }
        // 2.2.支付失败
        return PayResultDTO.builder()
                .status(PayStatus.TRADE_CLOSED.getValue())
                .msg(payOrder.getResultMsg())
                .build();
    }

    @Override
    public PayOrder queryByPayOrderNo(Long payOrderNo) {
        // R9: 通知/退款路径下高频读，走分桶本地缓存
        return payOrderGuard.get(payOrderNo, () ->
                lambdaQuery().eq(PayOrder::getPayOrderNo, payOrderNo).one());
    }

    @Override
    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        // R23: 先记录旧 bizOrderNo，写完 DB 后用 bizOrderNo 精准删缓存
        // （不能直接用 id 删，因为 cache key 是 bizOrderNo）。
        PayOrder before = getById(id);
        Long bizOrderNo = before == null ? null : before.getBizOrderNo();
        boolean ok = lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
                .set(PayOrder::getNotifyStatus, NotifyStatus.CALLING.getValue())
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
        if (ok) {
            // Cache-Aside 写路径：先写 DB，再删缓存（避免「DB 与缓存不一致时缓存长期覆盖 DB」）
            evictBizOrderCache(bizOrderNo);
        }
        return ok;
    }

    @Override
    public PageDTO<PayOrder> queryPayingOrderByPage(int pageNo, int size) {
        // 1.分页和排序条件
        Page<PayOrder> page = new Page<>(pageNo, size);
        page.addOrder(OrderItem.asc("id"));
        // 2.查询
        Page<PayOrder> result = lambdaQuery()
                .eq(PayOrder::getStatus, PayStatus.WAIT_BUYER_PAY.getValue())
                .page(page);
        return PageDTO.of(result);
    }

    @Override
    @Lock(name = PayConstants.RedisKeyFormatter.PAY_ORDER_CHECK_TASK, lockStrategy = LockStrategy.SKIP_AFTER_RETRY_TIMEOUT)
    public void checkPayOrder(PayOrder payOrder) {
        // 1.选择支付渠道
        IPayService payService = payServiceChannels.get(payOrder.getPayChannelCode());
        if (payService == null) {
            log.error("支付渠道不存在，业务单号：{}", payOrder.getBizOrderNo());
            // 异常订单，需要关闭支付单
            closeOrder(payOrder.getId());
            return;
        }
        // 2.判断订单是否超时
        if (payOrder.getPayOverTime().isBefore(LocalDateTime.now())) {
            log.debug("支付单{}已经超时，关闭订单", payOrder.getPayOrderNo());
            closeOrder(payOrder.getId());
            return;
        }
        // 3.查询支付状态
        PayStatusResponse response = payService.queryPayOrderStatus(payOrder.getPayOrderNo().toString());
        Integer payStatus = response.getPayStatus();
        // 3.1.判断是否查询失败或者正在支付
        if (!response.isSuccess() || PayStatus.WAIT_BUYER_PAY.equalsValue(payStatus)) {
            // 查询异常或正在支付，结束
            return;
        }
        // 3.2.判断支付状态是否变更
        if (payStatus.equals(payOrder.getStatus())) {
            // 支付状态没有变更
            return;
        }
        // 3.3.状态是支付成功或失败，直接更新订单状态
        updatePayStatus2DB(response, payOrder.getId());
        // 3.4.判断状态是否是成功，成功需要发送MQ消息通知
        if (PayStatus.TRADE_SUCCESS.equalsValue(response.getPayStatus())) {
            rabbitMqHelper.send(
                    MqConstants.Exchange.PAY_EXCHANGE,
                    MqConstants.Key.PAY_SUCCESS,
                    PayResultDTO.builder()
                            .payOrderNo(payOrder.getPayOrderNo())
                            .bizOrderId(payOrder.getBizOrderNo())
                            .payChannel(payOrder.getPayChannelCode())
                            .successTime(response.getSuccessTime())
                            .build()
            );
        }
    }

    private void updatePayStatus2DB(PayStatusResponse response, Long id) {
        try {
            // 写前先取一次，拿到 bizOrderNo 用于写后删缓存
            PayOrder before = getById(id);
            lambdaUpdate()
                    .set(PayOrder::getStatus, response.getPayStatus())
                    .set(PayOrder::getResultCode, response.getCode() == null ? "" : response.getCode())
                    .set(PayOrder::getResultMsg, response.getMsg() == null ? "" : response.getMsg())
                    .eq(PayOrder::getId, id)
                    .update();
            // R23: 写 DB 成功后删缓存
            if (before != null) {
                evictBizOrderCache(before.getBizOrderNo());
            }
        } catch (Exception e) {
            log.error("更新支付单结果到数据时发生异常", e);
        }
    }

    private void closeOrder(Long id) {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(id);
        payOrder.setStatus(PayStatus.TRADE_CLOSED.getValue());
        updateById(payOrder);
    }
}
