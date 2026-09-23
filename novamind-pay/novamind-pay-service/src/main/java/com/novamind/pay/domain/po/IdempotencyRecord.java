package com.novamind.pay.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 支付幂等键记录（P0 改造）
 * </p>
 *
 * <p>对应 docker/mysql/init/05-novamind-pay-idempotency.sql 中的 {@code idempotency_record} 表，
 * 用于微信/支付宝支付回调去重。唯一键：uk_biz_key(biz_type, idem_key)。</p>
 *
 * <p>status 取值见建表注释：0=处理中 1=成功 2=失败</p>
 *
 * @author P0-refactor
 * @since 2026-08-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("idempotency_record")
public class IdempotencyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，数据库自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 幂等键（外部传入 / 业务拼接），如支付回调的交易单号 out_trade_no
     */
    private String idemKey;

    /**
     * 业务类型: PAY_CALLBACK / REFUND_CALLBACK ...（本项目支付通知固定 PAY_NOTIFY）
     */
    private String bizType;

    /**
     * 业务单号（订单号 / 退款单号）
     */
    private String bizId;

    /**
     * 状态，0=处理中 1=成功 2=失败
     */
    private Integer status;

    /**
     * 成功响应或错误堆栈（截断 4k）
     */
    private String result;

    /**
     * 回写给微信/支付宝的原文
     */
    private String responseToChannel;

    /**
     * 创建时间（DB 默认 CURRENT_TIMESTAMP）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（DB ON UPDATE CURRENT_TIMESTAMP）
     */
    private LocalDateTime updateTime;
}
