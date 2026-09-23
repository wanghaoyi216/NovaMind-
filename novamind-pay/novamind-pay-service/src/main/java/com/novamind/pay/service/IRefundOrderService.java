package com.novamind.pay.service;

import com.novamind.common.domain.dto.PageDTO;
import com.novamind.pay.domain.po.RefundOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.pay.sdk.dto.RefundApplyDTO;
import com.novamind.pay.sdk.dto.RefundResultDTO;

/**
 * <p>
 * 退款订单 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-26
 */
public interface IRefundOrderService extends IService<RefundOrder> {

    RefundResultDTO applyRefund(RefundApplyDTO refundApplyDTO);

    RefundResultDTO queryRefundResult(Long bizRefundOrderId);

    RefundOrder queryByRefundOrderNo(Long refundOrderNo);

    PageDTO<RefundOrder> queryRefundingOrderByPage(int pageNo, int size);

    void checkRefundOrder(RefundOrder refundOrder);

    RefundResultDTO queryRefundDetail(Long bizRefundOrderId);
}
