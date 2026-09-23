package com.novamind.message.service;

import com.novamind.api.dto.sms.SmsInfoDTO;
import com.novamind.api.dto.user.UserDTO;
import com.novamind.message.domain.po.NoticeTemplate;

import java.util.List;

public interface ISmsService {
    void sendMessageByTemplate(NoticeTemplate noticeTemplate, List<UserDTO> users);

    void sendMessage(SmsInfoDTO smsInfoDTO);

    void sendMessageAsync(SmsInfoDTO smsInfoDTO);
}
