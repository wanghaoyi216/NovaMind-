package com.novamind.aigc.service;

import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.utils.UserContext;
import reactor.core.publisher.Flux;

public interface ChatService {

    /**
     * 获取对话id，规则：用户id_会话id
     *
     * @param sessionId 会话id
     * @return 对话id
     */
    static String getConversationId(String sessionId) {
        return UserContext.getUser() + "_" + sessionId;
    }

    /**
     * 聊天
     *
     * @param question  问题
     * @param sessionId 会话id
     * @return 回答内容
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    /**
     * 增强聊天入口，支持多模态输入与客户端元数据。默认降级为文本聊天以兼容旧实现。
     */
    default Flux<ChatEventVO> chat(ChatDTO chatDTO) {
        return chat(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

    /**
     * 停止生成
     *
     * @param sessionId 会话id
     */
    void stop(String sessionId);

    /**
     * 文本对话
     *
     * @param question 问题
     * @return 文本结果
     */
    String chatText(String question);
}
