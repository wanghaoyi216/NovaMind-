package com.novamind.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.promotion.domain.po.Coupon;
import com.novamind.promotion.domain.po.ExchangeCode;
import com.novamind.promotion.domain.query.CodeQuery;
import com.novamind.promotion.domain.vo.ExchangeCodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {
    void asyncGenerateCode(Coupon coupon);

    boolean updateExchangeMark(long serialNum, boolean mark);

    PageDTO<ExchangeCodeVO> queryCodePage(CodeQuery query);

    Long exchangeTargetId(long serialNum);
}
