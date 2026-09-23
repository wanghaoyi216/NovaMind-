package com.novamind.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.RagProperties;
import com.novamind.aigc.config.SystemPromptConfig;
import com.novamind.aigc.config.ToolResultHolder;
import com.novamind.aigc.constants.Constant;
import com.novamind.aigc.constants.RagConstants;
import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.quota.TokenQuotaService;
import com.novamind.aigc.service.AiStreamTelemetry;
import com.novamind.aigc.service.ChatMediaSupport;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.service.ChatSessionService;
import com.novamind.aigc.service.IntentClassifier;
import com.novamind.aigc.service.UserGraphService;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.LinkedHashMap;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ENHANCE")
public class ChatServiceImpl implements ChatService {

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    @Autowired(required = false)
    @Qualifier("chatClient")
    private ChatClient chatClient;

    @Autowired(required = false)
    @Qualifier("openAiChatClient")
    private ChatClient openAiChatClient;

    @Autowired(required = false)
    @Qualifier("nvidiaOpenAiChatClient")
    private ChatClient nvidiaOpenAiChatClient;

    @Autowired(required = false)
    @Qualifier("nvidiaChatClient")
    private ChatClient nvidiaChatClient;

    @Autowired
    private SystemPromptConfig systemPromptConfig;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.novamind.aigc.advisor.AdvisorFactory advisorFactory;

    @Autowired
    private com.novamind.aigc.config.RagProperties ragProperties;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMediaSupport chatMediaSupport;

    @Autowired
    private AiStreamTelemetry aiStreamTelemetry;

    @Autowired
    private UserGraphService userGraphService;

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private com.novamind.aigc.security.PromptInjectionGuard promptInjectionGuard;

    @Autowired
    private TokenQuotaService tokenQuotaService;

    private ChatClient getPrimaryChatClient() {
        if (this.nvidiaChatClient != null) {
            return this.nvidiaChatClient;
        }
        if (this.chatClient != null) {
            return this.chatClient;
        }
        throw new IllegalStateException("No ChatClient bean available. Please check tj.ai.chat-provider configuration.");
    }

    private ChatClient getTextChatClient() {
        if (this.nvidiaOpenAiChatClient != null) {
            return this.nvidiaOpenAiChatClient;
        }
        if (this.openAiChatClient != null) {
            return this.openAiChatClient;
        }
        return getPrimaryChatClient();
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return chat(ChatDTO.builder()
                .question(question)
                .sessionId(sessionId)
                .build());
    }

    @Override
    public Flux<ChatEventVO> chat(ChatDTO chatDTO) {
        // 获取会话Id和用户问题
        var question = StrUtil.blankToDefault(chatDTO.getQuestion(), ""); // 非空判断，blankToDefault
        var sessionId = chatDTO.getSessionId();
        var conversationId = ChatService.getConversationId(sessionId);
        var requestId = IdUtil.simpleUUID();
        var userId = UserContext.getUser();

        // P0 安全加固：Prompt Injection 输入侧过滤（在意图识别之前执行，
        // 保证注入文本不进入 RAG 检索 / 图谱上下文 / ChatMemory 任一环节）
        var sanitizeResult = this.promptInjectionGuard.sanitize(question);
        if (sanitizeResult.tainted()) {
            log.warn("[chat] prompt injection detected & sanitized. userId={}, sessionId={}, hits={}",
                    userId, sessionId, sanitizeResult.hits());
            question = sanitizeResult.cleaned();
            chatDTO.setQuestion(question);
        }

        // 配额治理（移植自墨问 per-user LLM Token 配额）：按 question 长度估算 token
        // （约 4 字符/token），在意图识别之前做"每用户每日"配额检查。超限抛
        // BadRequestException 由 CommonExceptionAdvice 统一转错误响应；userId 为 null
        // （未登录）时服务内部跳过；Redis 异常 fail-open 放行。
        this.tokenQuotaService.checkAndDeduct(userId, question.length() / 4);

        // 意图识别 - 判断是否需要 RAG
        var intentResult = this.intentClassifier.classifyIntent(question);
        var useRag = this.intentClassifier.shouldUseRag(question);
        log.info("意图识别结果: {}, 使用RAG: {}, 问题: {}", intentResult, useRag, question);

        // 构建拦截器 - 根据意图决定是否使用 RAG
        var qaAdvisor = useRag ? this.advisorFactory.buildQuestionAnswerAdvisor() : null;
        var graphContext = this.userGraphService.loadPromptContext(userId, chatDTO);
        var userText = this.chatMediaSupport.buildUserText(question, chatDTO.getMedia());
        var media = this.chatMediaSupport.toSpringAiMedia(chatDTO.getMedia());
        var telemetry = this.aiStreamTelemetry.start(userText + "\n" + graphContext);

        // 更新会话
        this.chatSessionService.update(sessionId, question, userId);

        var pipeline = com.novamind.aigc.service.support.ChatStreamPipeline.builder()
                .stringRedisTemplate(this.stringRedisTemplate)
                .chatMemory(this.chatMemory)
                .sessionId(sessionId)
                .requestId(requestId)
                .conversationId(conversationId)
                .telemetryTracker(telemetry)
                .onCompleteHook(finalAnswer -> this.userGraphService.recordConversation(userId, chatDTO, finalAnswer))
                .includeMetrics(true)
                .stopEvent(ChatEventVO.of(ChatEventTypeEnum.STOP, null, requestId, "FINAL"))
                .build();

        // 远程调用，获取stream流的输出
        var client = getPrimaryChatClient();
        var promptBuilder = client.prompt()
                .system(promptSystem -> promptSystem
                        .text(buildSystemPrompt(graphContext))
                        .params(Map.of("now", DateUtil.now(), "graphContext", graphContext))
                )
                .toolContext(Map.of(
                        Constant.REQUEST_ID, requestId,
                        Constant.USER_ID, userId))
                .user(user -> {
                    user.text(userText);
                    if (!media.isEmpty()) {
                        user.media(media.toArray(Media[]::new));
                    }
                });

        // 只有在需要 RAG 时才添加 qaAdvisor
        if (useRag && qaAdvisor != null) {
            promptBuilder.advisors(advisor -> advisor
                    .advisors(qaAdvisor)
                    .param(ChatMemory.CONVERSATION_ID, conversationId));
        } else {
            promptBuilder.advisors(advisor -> advisor
                    .param(ChatMemory.CONVERSATION_ID, conversationId));
        }

        var stream = promptBuilder.stream().chatResponse();

        // 发送给前端数据的一整个流程
        return Flux.concat(
                // 封装给前端的前置准备，让前端播放动画
                Flux.just(
                        ChatEventVO.of(ChatEventTypeEnum.THOUGHT, "分析用户输入、会话记忆与个性化图谱上下文", requestId, "PREPARE", prepareMetadata(media.size(), graphContext, useRag, intentResult)),
                        ChatEventVO.of(ChatEventTypeEnum.ACTION, useRag ? "启用知识库检索增强，调用大模型生成回答" : "直接调用大模型生成回答", requestId, "MODEL"),
                        ChatEventVO.of(ChatEventTypeEnum.METRICS, telemetry.snapshot(), requestId, "METRICS")
                ),
                // 开始流式输出模型响应
                pipeline.execute(stream)
        );
    }

    // 停止会话
    @Override
    public void stop(String sessionId) {
        var generateStatusOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        generateStatusOps.delete(sessionId);
    }

    // 不触发流式调用，直接返回大模型生成信息
    @Override
    public String chatText(String question) {
        var client = getTextChatClient();
        return client.prompt()
                .system(this.systemPromptConfig.getTextSystemMessage().get())
                .user(question)
                .call()
                .content();
    }

    // 构建系统提示词
    private String buildSystemPrompt(String graphContext) {
        var systemPrompt = this.systemPromptConfig.getChatSystemMessage().get();
        if (StrUtil.isBlank(graphContext)) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n" + graphContext;
    }

    // 准备元数据
    private Map<String, Object> prepareMetadata(int mediaCount, String graphContext, boolean useRag, String intentResult) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("mediaCount", mediaCount);
        metadata.put("graphContextAttached", StrUtil.isNotBlank(graphContext));
        metadata.put(RagConstants.METADATA_RAG_ENABLED, useRag);
        metadata.put(RagConstants.METADATA_INTENT_TYPE, intentResult);
        if (useRag) {
            metadata.put(RagConstants.METADATA_RAG_TOP_K, this.ragProperties.getTopK());
            metadata.put(RagConstants.METADATA_RAG_SIMILARITY_THRESHOLD, this.ragProperties.getSimilarityThreshold());
        }
        return metadata;
    }

}
