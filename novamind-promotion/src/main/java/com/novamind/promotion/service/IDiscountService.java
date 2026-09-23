package com.novamind.promotion.service;

import com.novamind.api.dto.promotion.CouponDiscountDTO;
import com.novamind.api.dto.promotion.OrderCouponDTO;
import com.novamind.api.dto.promotion.OrderCourseDTO;

import java.util.List;

public interface IDiscountService {
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses);

    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);
}
