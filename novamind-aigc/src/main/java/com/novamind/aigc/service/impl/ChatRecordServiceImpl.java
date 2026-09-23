package com.novamind.aigc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novamind.aigc.entity.ChatRecord;
import com.novamind.aigc.enums.MessageTypeEnum;
import com.novamind.aigc.mapper.ChatRecordMapper;
import com.novamind.aigc.memory.MyMessage;
import com.novamind.aigc.service.ChatRecordService;
import com.novamind.aigc.vo.MessageVO;
import com.novamind.common.domain.dto.PageDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ChatRecordServiceImpl extends ServiceImpl<ChatRecordMapper, ChatRecord> implements ChatRecordService {

    /**
     * 根据对话Id查询分页聊天历史记录
     * @param conversationId 对话ID（userId_sessionId）
     * @param page           页码（从1开始）
     * @param size           每页条数
     * @return
     */
    @Override
    public PageDTO<MessageVO> pageByConversationId(String conversationId, int page, int size) {
        Page<ChatRecord> pageParam = Page.of(page, size);

        Page<ChatRecord> result = lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByDesc(ChatRecord::getCreateTime)
                .page(pageParam);

        if (CollUtil.isEmpty(result.getRecords())) {
            return PageDTO.empty(result);
        }

        List<MessageVO> list = result.getRecords().stream()
                .map(this::toMessageVO)
                .filter(Objects::nonNull)
                .toList();

        return PageDTO.of(result, list);
    }

    private MessageVO toMessageVO(ChatRecord record) {
        MyMessage myMessage = JSONUtil.toBean(record.getData(), MyMessage.class);
        MessageTypeEnum type;
        try {
            type = MessageTypeEnum.valueOf(myMessage.getMessageType());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return MessageVO.builder()
                .type(type)
                .content(myMessage.getTextContent())
                .params(myMessage.getParams())
                .build();
    }
}
