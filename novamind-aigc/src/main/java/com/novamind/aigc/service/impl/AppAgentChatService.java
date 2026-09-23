package com.novamind.aigc.service.impl;

import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.utils.JsonUtils;
import com.novamind.aigc.config.DashScopeProperties;
import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.utils.TokenContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "APP")
public class AppAgentChatService implements ChatService {

    private final DashScopeProperties dashScopeProperties;

    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return chat(ChatDTO.builder().question(question).sessionId(sessionId).build());
    }

    @Override
    public Flux<ChatEventVO> chat(ChatDTO chatDTO) {
        var question = chatDTO.getQuestion();
        var sessionId = chatDTO.getSessionId();
        var conversationId = ChatService.getConversationId(sessionId);
        var token = TokenContext.getToken();

        var toolsMap = new HashMap<String, Object>();
        for (var tool : dashScopeProperties.getAppAgent().getTools()) {
            toolsMap.put(tool, Map.of("user_token", token));
        }
        var bizParams = Map.of("user_defined_tokens", toolsMap);

        var param = ApplicationParam.builder()
                .apiKey(dashScopeProperties.getKey())
                .appId(dashScopeProperties.getAppAgent().getId())
                .prompt(question)
                .incrementalOutput(true)
                .bizParams(JsonUtils.toJsonObject(bizParams))
                .sessionId(conversationId)
                .build();

        var application = new Application();
        try {
            var result = application.streamCall(param);
            return Flux.from(result)
                    .doFirst(() -> GENERATE_STATUS.put(sessionId, true))
                    .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))
                    .doOnError(throwable -> GENERATE_STATUS.remove(sessionId))
                    .takeWhile(s -> GENERATE_STATUS.getOrDefault(sessionId, false))
                    .map(applicationResult -> {
                        var text = applicationResult.getOutput().getText();
                        return ChatEventVO.of(ChatEventTypeEnum.DATA, text, null, "STREAM");
                    })
                    .concatWith(Flux.just(ChatEventVO.of(ChatEventTypeEnum.STOP, null, null, "FINAL")));
        } catch (Exception e) {
            throw new RuntimeException("DashScope stream call failed", e);
        }
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    @Override
    public String chatText(String question) {
        return "";
    }

}
