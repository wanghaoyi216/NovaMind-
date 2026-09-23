package com.novamind.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.api.dto.course.CoursePurchaseInfoDTO;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.trade.domain.po.Order;
import com.novamind.trade.domain.po.OrderDetail;
import com.novamind.trade.domain.po.RefundApply;
import com.novamind.trade.domain.query.OrderDetailPageQuery;
import com.novamind.trade.domain.vo.OrderDetailAdminVO;
import com.novamind.trade.domain.vo.OrderDetailPageVO;
import com.novamind.trade.domain.vo.OrderProgressNodeVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 订单明细 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface IOrderDetailService extends IService<OrderDetail> {

    void updateStatusByOrderId(Long orderId, Integer status);

    List<OrderDetail> queryByOrderIds(List<Long> orderIds);

    List<OrderDetail> queryByOrderId(Long orderId);

    PageDTO<OrderDetailPageVO> queryDetailForPage(OrderDetailPageQuery pageQuery);

    OrderDetailAdminVO queryOrdersDetailProgress(Long id);

    List<OrderProgressNodeVO> packageProgressNodes(Order order, RefundApply refundApply);

    void markDetailSuccessByOrderId(Long id, String payChannel, LocalDateTime successTime);

    void updateRefundStatusById(Long orderDetailId, int status);

    List<Long> queryCourseIdsByOrderId(Long orderId);

    Boolean checkCourseOrderInfo(Long courseId);

    Map<Long, Integer> countEnrollNumOfCourse(List<Long> courseIdList);

    Map<Long, Integer> countEnrollCourseOfStudent(List<Long> studentIds);

    CoursePurchaseInfoDTO getPurchaseInfoOfCourse(Long courseId);
}
