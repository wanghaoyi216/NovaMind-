package com.novamind.aigc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novamind.aigc.config.SessionProperties;
import com.novamind.aigc.entity.ChatRecord;
import com.novamind.aigc.entity.ChatSession;
import com.novamind.aigc.enums.MessageTypeEnum;
import com.novamind.aigc.mapper.ChatSessionMapper;
import com.novamind.aigc.memory.MessageUtil;
import com.novamind.aigc.memory.MyAssistantMessage;
import com.novamind.aigc.memory.MyMessage;
import com.novamind.aigc.service.ChatRecordService;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.service.ChatSessionService;
import com.novamind.aigc.vo.ChatSessionVO;
import com.novamind.aigc.vo.ExportSessionVO;
import com.novamind.aigc.vo.MessageVO;
import com.novamind.aigc.vo.SessionVO;
import com.novamind.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;
    private final ChatMemory chatMemory;
    private final ChatRecordService chatRecordService;

    @Override
    public SessionVO createSession(Integer num) {
        var sessionVO = BeanUtil.toBean(this.sessionProperties, SessionVO.class);
        sessionVO.setExamples(RandomUtil.randomEleList(this.sessionProperties.getExamples(), num));
        sessionVO.setSessionId(IdUtil.simpleUUID());

        var chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(UserContext.getUser())
                .build();
        super.save(chatSession);

        return sessionVO;
    }

    @Override
    public List<SessionVO.Example> hotExamples(Integer num) {
        return RandomUtil.randomEleList(this.sessionProperties.getExamples(), num);
    }

    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        var conversationId = ChatService.getConversationId(sessionId);
        var messageList = this.chatMemory.get(conversationId);
        return StreamUtil.of(messageList)
                .filter(message ->
                        message.getMessageType() == MessageType.ASSISTANT
                                || message.getMessageType() == MessageType.USER)
                .map(message -> {
                    if (message instanceof MyAssistantMessage myAssistantMessage) {
                        return MessageVO.builder()
                                .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                                .content(message.getText())
                                .params(myAssistantMessage.getParams())
                                .build();
                    }
                    return MessageVO.builder()
                            .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                            .content(message.getText())
                            .build();
                })
                .toList();
    }

    @Async
    @Override
    public void update(String sessionId, String title, Long userId) {
        var chatSession = super.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .one();
        if (chatSession == null) {
            return;
        }
        if (StrUtil.isEmpty(chatSession.getTitle()) && StrUtil.isNotEmpty(title)) {
            chatSession.setTitle(StrUtil.sub(title, 0, 100));
        }
        chatSession.setUpdateTime(LocalDateTime.now());
        super.updateById(chatSession);
    }

    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        var userId = UserContext.getUser();
        var chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT 30")
                .list();

        if (CollUtil.isEmpty(chatSessionList)) {
            return Map.of();
        }

        var chatSessionVOList = CollStreamUtil.toList(chatSessionList, chatSession -> ChatSessionVO.builder()
                .title(chatSession.getTitle())
                .updateTime(chatSession.getUpdateTime())
                .sessionId(chatSession.getSessionId())
                .build());

        var now = LocalDateTime.now().toLocalDate();
        return CollStreamUtil.groupByKey(chatSessionVOList, chatSessionVO -> {
            var days = Math.abs(ChronoUnit.DAYS.between(chatSessionVO.getUpdateTime().toLocalDate(), now));
            return com.novamind.aigc.enums.SessionPeriodGroup.getDescByDays(days);
        });
    }

    @Override
    public void deleteHistorySession(String sessionId) {
        var userId = UserContext.getUser();
        var queryWrapper = Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId);
        super.remove(queryWrapper);

        var conversationId = ChatService.getConversationId(sessionId);
        this.chatMemory.clear(conversationId);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        super.lambdaUpdate()
                .set(ChatSession::getTitle, StrUtil.sub(title, 0, 100))
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser())
                .update();
    }

    // 根据用户Id和会话Id查询用户聊天内容
    @Override
    public ExportSessionVO getSession(String sessionId) {
        Long userId = UserContext.getUser();
        // 获取对话标题
        String title = this.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getSessionId, sessionId)
                .one()
                .getTitle();
        // 获取对话Id
        String conversationId = ChatService.getConversationId(sessionId);
        // 获取所有对话记录
        List<ChatRecord> records = this.chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByAsc(ChatRecord::getCreateTime)
                .list();

        return new ExportSessionVO(userId, sessionId, buildExportContent(title, records));
    }

    // 封装导出内容
    private String buildExportContent(String title, List<ChatRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");

        for (ChatRecord record : records) {
            MyMessage message = JSONUtil.toBean(record.getData(), MyMessage.class);
            String messageType = message.getMessageType();

            // 只处理用户消息和AI回复
            if ("USER".equals(messageType)) {
                sb.append("**用户**: ").append(message.getTextContent()).append("\n\n");
            } else if ("ASSISTANT".equals(messageType)) {
                sb.append("**AI助手**: ").append(message.getTextContent()).append("\n\n");
            }
        }
        return sb.toString();
    }
}