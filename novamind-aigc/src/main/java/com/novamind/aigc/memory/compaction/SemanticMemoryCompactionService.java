package com.novamind.aigc.memory.compaction;

import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.ContextWindowProperties;
import com.novamind.aigc.memory.MyChatMemoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class SemanticMemoryCompactionService {

    private final ConcurrentHashMap.KeySetView<String, Boolean> runningConversations = ConcurrentHashMap.newKeySet();

    @Resource
    private MyChatMemoryRepository chatMemoryRepository;
    @Resource
    private ContextWindowProperties properties;
    @Resource
    private ContextTokenEstimator tokenEstimator;

    @Autowired(required = false)
    @Qualifier("nvidiaOpenAiChatClient")
    private ChatClient nvidiaSummarizerClient;

    @Autowired(required = false)
    @Qualifier("openAiChatClient")
    private ChatClient openAiSummarizerClient;

    private ChatClient getSummarizerClient() {
        if (this.nvidiaSummarizerClient != null) {
            return this.nvidiaSummarizerClient;
        }
        if (this.openAiSummarizerClient != null) {
            return this.openAiSummarizerClient;
        }
        throw new IllegalStateException("No summarizer ChatClient available.");
    }
    @Resource(name = "aiContextCompactionExecutor")
    private Executor compactionExecutor;

    public void compactIfNecessary(String conversationId) {
        if (!properties.isCompactionEnabled() || StrUtil.isBlank(conversationId)) {
            return;
        }
        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
        var metrics = tokenEstimator.estimate(messages);
        if (!metrics.shouldCompact(properties.getCompactionThresholdRatio(), properties.getMinMessagesBeforeCompaction())) {
            return;
        }
        if (!runningConversations.add(conversationId)) {
            return;
        }
        compactionExecutor.execute(() -> compact(conversationId));
    }

    private void compact(String conversationId) {
        try {
            List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
            var metrics = tokenEstimator.estimate(messages);
            if (!metrics.shouldCompact(properties.getCompactionThresholdRatio(), properties.getMinMessagesBeforeCompaction())) {
                return;
            }
            int retainCount = Math.min(Math.max(1, properties.getRetainRecentMessages()), messages.size() - 1);
            int splitIndex = messages.size() - retainCount;
            if (splitIndex <= 0) {
                return;
            }

            List<Message> historicalMessages = messages.subList(0, splitIndex);
            List<Message> recentMessages = messages.subList(splitIndex, messages.size());
            String transcript = buildTranscript(historicalMessages);
            if (StrUtil.isBlank(transcript)) {
                return;
            }

            String summary = getSummarizerClient().prompt()
                    .system(properties.getSummarySystemPrompt())
                    .user(transcript)
                    .call()
                    .content();
            if (StrUtil.isBlank(summary)) {
                log.warn("Skip context compaction because summarizer returned empty content. conversationId={}", conversationId);
                return;
            }

            var compactedMessages = new ArrayList<Message>(recentMessages.size() + 1);
            compactedMessages.add(new SystemMessage(buildMemorySummary(summary, metrics)));
            compactedMessages.addAll(recentMessages);
            chatMemoryRepository.saveAll(conversationId, compactedMessages);

            var compactedMetrics = tokenEstimator.estimate(compactedMessages);
            log.info("Context compacted. conversationId={}, beforeTokens={}, afterTokens={}, beforeRatio={}, afterRatio={}",
                    conversationId,
                    metrics.usedTokens(),
                    compactedMetrics.usedTokens(),
                    String.format("%.2f", metrics.usedRatio()),
                    String.format("%.2f", compactedMetrics.usedRatio()));
        } catch (Exception e) {
            log.error("Context compaction failed. conversationId={}", conversationId, e);
        } finally {
            runningConversations.remove(conversationId);
        }
    }

    private String buildTranscript(List<Message> messages) {
        int maxChars = Math.max(1, properties.getMaxSummarySourceTokens() * properties.getEstimatedCharsPerToken());
        var builder = new StringBuilder(Math.min(maxChars, 4096));
        for (Message message : messages) {
            if (message == null || StrUtil.isBlank(message.getText())) {
                continue;
            }
            builder.append('[')
                    .append(message.getMessageType().name())
                    .append("] ")
                    .append(message.getText())
                    .append('\n');
            if (builder.length() >= maxChars) {
                builder.setLength(maxChars);
                builder.append("\n[TRUNCATED]");
                break;
            }
        }
        return builder.toString();
    }

    private String buildMemorySummary(String summary, ContextWindowMetrics metrics) {
        return """
                [Compacted conversation memory]
                Source messages: %d
                Source estimated tokens: %d/%d

                %s
                """.formatted(metrics.messageCount(), metrics.usedTokens(), metrics.maxWindowTokens(), summary);
    }
}
