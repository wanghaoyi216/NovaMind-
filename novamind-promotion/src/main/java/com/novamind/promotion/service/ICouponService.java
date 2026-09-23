package com.novamind.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.promotion.domain.dto.CouponFormDTO;
import com.novamind.promotion.domain.dto.CouponIssueFormDTO;
import com.novamind.promotion.domain.po.Coupon;
import com.novamind.promotion.domain.query.CouponQuery;
import com.novamind.promotion.domain.vo.CouponDetailVO;
import com.novamind.promotion.domain.vo.CouponPageVO;
import com.novamind.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 虎哥
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO dto);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(CouponIssueFormDTO dto);

    List<CouponVO> queryIssuingCoupons();

    void pauseIssue(Long id);

    void deleteById(Long id);

    CouponDetailVO queryCouponById(Long id);

    void beginIssueBatch(List<Coupon> coupons);
}
