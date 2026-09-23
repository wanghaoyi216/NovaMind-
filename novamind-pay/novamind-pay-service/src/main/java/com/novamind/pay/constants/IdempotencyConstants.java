package com.novamind.pay.constants;

/**
 * <h1>支付幂等常量定义 (P0 改造)</h1>
 */
public interface IdempotencyConstants {
    /** 业务类型：支付通知回调 */
    String IDEM_BIZ_TYPE_PAY_NOTIFY = "PAY_NOTIFY";
    /** 业务类型：退款通知回调（预留） */
    String IDEM_BIZ_TYPE_REFUND_NOTIFY = "REFUND_NOTIFY";

    /** 状态：0=处理中 */
    int IDEM_STATUS_PENDING = 0;
    /** 状态：1=成功 */
    int IDEM_STATUS_SUCCESS = 1;
    /** 状态：2=失败 */
    int IDEM_STATUS_FAILED = 2;
}
