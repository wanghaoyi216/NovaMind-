package com.novamind.trade.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novamind.api.dto.IdAndNumDTO;
import com.novamind.trade.domain.po.OrderDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 订单明细 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    @Select("SELECT course_id FROM order_detail WHERE order_id = #{orderId}")
    List<Long> queryCourseIdsByOrderId(Long orderId);

    List<IdAndNumDTO> countEnrollNumOfCourse(@Param("ew") QueryWrapper<OrderDetail> wrapper);

    List<IdAndNumDTO> countEnrollCourseOfStudent(@Param("ew") QueryWrapper<OrderDetail> wrapper);

    /**
     * 课程实付总额。注意必须用 COALESCE 兜底：SUM 在没有任何已支付记录时返回 NULL，
     * 而方法签名是基本类型 int，MyBatis 会直接抛
     * {@code BindingException: attempted to return null from a method with a primitive return type}。
     * 这会让课程详情接口（/courses/baseInfo/{id}）在「没有任何人买过这门课」时 500。
     */
    @Select("SELECT COALESCE(SUM(real_pay_amount), 0) FROM order_detail WHERE course_id = #{courseId}")
    int countRealPayAmountByCourseId(Long courseId);
}
