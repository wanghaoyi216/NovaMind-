package com.novamind.aigc.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.ToolResultHolder;
import com.novamind.aigc.config.ContextWindowProperties;
import com.novamind.aigc.constants.Constant;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.service.ChatSessionService;
import com.novamind.aigc.tools.registry.AiToolMetadata;
import com.novamind.aigc.tools.registry.AiToolRegistry;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
// 抽象Agent类
public abstract class AbstractAgent implements Agent {

    // 聊天事件VO：代表事件停止
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder()
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .eventName(ChatEventTypeEnum.STOP.name())
            .phase("FINAL")
            .build();

    // GENERATE_STATUS_KEY
    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    @Autowired(required = false)
    @Qualifier("chatClient")
    private ChatClient chatClient;

    @Autowired(required = false)
    @Qualifier("nvidiaChatClient")
    private ChatClient nvidiaChatClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private AiToolRegistry aiToolRegistry;
    @Resource
    private ContextWindowProperties contextWindowProperties;

    // stream流式输出
    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId, String requestId) {
        var conversationId = ChatService.getConversationId(sessionId);
        var userId = UserContext.getUser();

        // 更新用户会话
        this.chatSessionService.update(sessionId, question, userId);

        var pipeline = com.novamind.aigc.service.support.ChatStreamPipeline.builder()
                .stringRedisTemplate(this.stringRedisTemplate)
                .chatMemory(this.chatMemory)
                .sessionId(sessionId)
                .requestId(requestId)
                .conversationId(conversationId)
                .includeMetrics(false)
                .stopEvent(STOP_EVENT)
                .build();

        var stream = getChatClientRequest(question, sessionId, requestId)
                .stream()
                .chatResponse();

        return pipeline.execute(stream);
    }

    @Override
    public String process(String question, String sessionId) {
        var requestId = IdUtil.fastSimpleUUID();
        var userId = UserContext.getUser();
        this.chatSessionService.update(sessionId, question, userId);

        return getChatClientRequest(question, sessionId, requestId)
                .call()
                .content();
    }

    @Override
    public void stop(String sessionId) {
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }

    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId) {
        var conversationId = ChatService.getConversationId(sessionId);
        return Map.of(ChatMemory.CONVERSATION_ID, conversationId);
    }

    private ChatClient getPrimaryChatClient() {
        if (this.nvidiaChatClient != null) {
            return this.nvidiaChatClient;
        }
        return this.chatClient;
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequest(String question, String sessionId, String requestId) {
        return getPrimaryChatClient().prompt()
                .system(promptSystemSpec ->
                        promptSystemSpec.text(this.systemMessageWithToolCatalog()).params(this.systemMessageParams()))
                .advisors(advisorSpec ->
                        advisorSpec.advisors(this.advisors()).params(this.advisorParams(sessionId, requestId)))
                .tools(this.resolveTools())
                .toolContext(this.toolContext(sessionId, requestId))
                .user(question);
    }

    private Object[] resolveTools() {
        return this.aiToolRegistry.resolveTools(this.getAgentType(), this.tools());
    }

    private String systemMessageWithToolCatalog() {
        var systemMessage = this.systemMessage();
        var metadata = this.aiToolRegistry.metadata(this.getAgentType(), this.contextWindowProperties.getMaxWindowTokens());
        if (metadata.isEmpty()) {
            return systemMessage;
        }
        var builder = new StringBuilder(systemMessage);
        builder.append("\n\nAvailable tools (lightweight catalog, use only when needed):");
        for (AiToolMetadata toolMetadata : metadata) {
            builder.append("\n- ")
                    .append(toolMetadata.getId())
                    .append(": ")
                    .append(toolMetadata.getName())
                    .append(" - ")
                    .append(toolMetadata.getDescription());
        }
        return builder.toString();
    }

    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }
}
