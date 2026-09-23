package com.novamind.trade.service;

import com.novamind.trade.domain.dto.OrderDelayQueryDTO;
import com.novamind.trade.domain.dto.PayApplyFormDTO;
import com.novamind.trade.domain.vo.PayChannelVO;

import java.util.List;

public interface IPayService {
    List<PayChannelVO> queryPayChannels();

    String applyPayOrder(PayApplyFormDTO payApply);

    void queryPayResult(OrderDelayQueryDTO message);
}
