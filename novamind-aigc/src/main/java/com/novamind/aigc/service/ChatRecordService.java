package com.novamind.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.aigc.entity.ChatRecord;
import com.novamind.aigc.vo.MessageVO;
import com.novamind.common.domain.dto.PageDTO;

/**
 * 对话记忆
 */
public interface ChatRecordService extends IService<ChatRecord> {

    /**
     * 按 conversationId 分页查询对话历史
     *
     * @param conversationId 对话ID（userId_sessionId）
     * @param page           页码（从1开始）
     * @param size           每页条数
     * @return 仅包含 USER / ASSISTANT 类型的消息分页结果
     */
    PageDTO<MessageVO> pageByConversationId(String conversationId, int page, int size);
}
