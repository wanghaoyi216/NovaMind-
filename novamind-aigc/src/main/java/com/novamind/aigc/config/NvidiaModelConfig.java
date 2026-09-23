package com.novamind.aigc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-provider", havingValue = "NVIDIA", matchIfMissing = false)
public class NvidiaModelConfig {

    private final NvidiaProperties nvidiaProperties;

    @Bean
    public OpenAiApi nvidiaOpenAiApi() {
        String apiKey = nvidiaProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "local-dev-placeholder".equals(apiKey)) {
            apiKey = System.getenv("NVIDIA_API_KEY2");
            log.info("NVIDIA API Key from environment variable: {}", apiKey != null ? "******" : "null");
        }
        // 兜底：OpenAiApi 要求 apiKey 非空（simpleApiKey cannot be null），
        // 无真实 key 时用项目统一的占位符启动，实际调用时报 401 但不影响服务启动。
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "local-dev-placeholder";
        }
        String baseUrl = nvidiaProperties.getBaseUrl();
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    private OpenAiChatModel buildChatModel(OpenAiApi api, String model) {
        org.springframework.ai.openai.OpenAiChatOptions options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @Primary
    @Qualifier("nvidiaChatModel")
    public ChatModel nvidiaChatModel(OpenAiApi nvidiaOpenAiApi) {
        return buildChatModel(nvidiaOpenAiApi, nvidiaProperties.getDefaultTextModel());
    }

    @Bean
    @Qualifier("nvidiaVisionChatModel")
    public ChatModel nvidiaVisionChatModel(OpenAiApi nvidiaOpenAiApi) {
        return buildChatModel(nvidiaOpenAiApi, nvidiaProperties.getDefaultVisionModel());
    }

    @Bean
    public Map<String, ChatModel> nvidiaChatModelRegistry(OpenAiApi nvidiaOpenAiApi) {
        Map<String, ChatModel> registry = new HashMap<>();

        if (nvidiaProperties.getTextModels() != null) {
            for (String model : nvidiaProperties.getTextModels()) {
                registry.put(model, buildChatModel(nvidiaOpenAiApi, model));
            }
        }

        if (nvidiaProperties.getVisionModels() != null) {
            for (String model : nvidiaProperties.getVisionModels()) {
                registry.put(model, buildChatModel(nvidiaOpenAiApi, model));
            }
        }

        log.info("NVIDIA ChatModel registry initialized with {} models", registry.size());
        return registry;
    }

}
