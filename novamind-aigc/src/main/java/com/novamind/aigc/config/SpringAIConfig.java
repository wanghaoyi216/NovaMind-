package com.novamind.aigc.config;

import com.novamind.aigc.advisor.RecordOptimizationAdvisor;
import com.novamind.aigc.advisor.ContextWindowAdvisor;
import com.novamind.aigc.memory.MyChatMemoryRepository;
import com.novamind.aigc.memory.RedisChatMemoryRepository;
import com.novamind.aigc.memory.jdbc.JdbcChatMemoryRepository;
import com.novamind.aigc.memory.mongodb.MongoDBChatMemoryRepository;
import com.novamind.aigc.memory.compaction.SemanticMemoryCompactionService;
import com.novamind.common.constants.Constant;
import com.novamind.common.utils.WebUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class SpringAIConfig {

    @Value("${tj.ai.memory.max:100}")
    private Integer maxMessages;

    @Bean
    public RetryListener customizeRetryTemplate(RetryTemplate retryTemplate) {
        RetryListener retryListener = new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
                WebUtils.setAttribute(Constant.SPRING_AI_ATTR, Constant.SPRING_AI_FLAG);
                return true;
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                WebUtils.removeAttribute(Constant.SPRING_AI_ATTR);
            }
        };
        retryTemplate.registerListener(retryListener);
        return retryListener;
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai", name = "chat-provider", havingValue = "DASHSCOPE", matchIfMissing = true)
    public ChatClient chatClient(
            @Qualifier("dashscopeChatModel") ChatModel dashScopeChatModel,
            Advisor loggerAdvisor,
            Advisor messageChatMemoryAdvisor,
            Advisor recordOptimizationAdvisor,
            Advisor contextWindowAdvisor) {
        return ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(loggerAdvisor, messageChatMemoryAdvisor, recordOptimizationAdvisor, contextWindowAdvisor)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai", name = "chat-provider", havingValue = "NVIDIA")
    public ChatClient nvidiaChatClient(
            @Qualifier("nvidiaChatModel") ChatModel nvidiaChatModel,
            Advisor loggerAdvisor,
            Advisor messageChatMemoryAdvisor,
            Advisor recordOptimizationAdvisor,
            Advisor contextWindowAdvisor) {
        return ChatClient.builder(nvidiaChatModel)
                .defaultAdvisors(loggerAdvisor, messageChatMemoryAdvisor, recordOptimizationAdvisor, contextWindowAdvisor)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai", name = "chat-provider", havingValue = "DASHSCOPE", matchIfMissing = true)
    public ChatClient openAiChatClient(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel,
            Advisor loggerAdvisor) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai", name = "chat-provider", havingValue = "NVIDIA")
    public ChatClient nvidiaOpenAiChatClient(
            @Qualifier("nvidiaChatModel") ChatModel nvidiaChatModel,
            Advisor loggerAdvisor) {
        return ChatClient.builder(nvidiaChatModel)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }

    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", value = "type", havingValue = "Redis")
    public MyChatMemoryRepository redisChatMemoryRepository() {
        return new RedisChatMemoryRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", value = "type", havingValue = "MYSQL")
    public MyChatMemoryRepository jdbcChatMemoryRepository() {
        return new JdbcChatMemoryRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.ai.memory", value = "type", havingValue = "MongoDB")
    public MyChatMemoryRepository mongoDBChatMemoryRepository() {
        return new MongoDBChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(this.maxMessages)
                .build();
    }

    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public Advisor recordOptimizationAdvisor(MyChatMemoryRepository myChatMemoryRepository) {
        return new RecordOptimizationAdvisor(myChatMemoryRepository);
    }

    @Bean
    public Advisor contextWindowAdvisor(SemanticMemoryCompactionService semanticMemoryCompactionService) {
        return new ContextWindowAdvisor(semanticMemoryCompactionService);
    }

    @Bean("aiContextCompactionExecutor")
    public Executor aiContextCompactionExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-context-compaction-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

}
