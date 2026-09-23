package com.novamind.aigc.service;

import com.novamind.aigc.config.ContextWindowProperties;
import com.novamind.aigc.memory.compaction.ContextTokenEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class AiStreamTelemetry {

    private final ContextTokenEstimator tokenEstimator;
    private final ContextWindowProperties contextWindowProperties;

    public Tracker start(String promptText) {
        return new Tracker(
                tokenEstimator.estimate(promptText),
                Math.max(1, contextWindowProperties.getMaxWindowTokens())
        );
    }

    public static class Tracker {
        private final long startedAt = System.nanoTime();
        private final int estimatedPromptTokens;
        private final int maxWindowTokens;
        private final AtomicInteger emittedChars = new AtomicInteger();
        private final AtomicReference<Integer> promptTokens = new AtomicReference<>();
        private final AtomicReference<Integer> completionTokens = new AtomicReference<>();

        private Tracker(int estimatedPromptTokens, int maxWindowTokens) {
            this.estimatedPromptTokens = Math.max(0, estimatedPromptTokens);
            this.maxWindowTokens = maxWindowTokens;
        }

        public void onChunk(ChatResponse response, String text) {
            if (text != null) {
                emittedChars.addAndGet(text.length());
            }
            if (response == null || response.getMetadata() == null) {
                return;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            if (usage.getPromptTokens() != null) {
                promptTokens.set(usage.getPromptTokens());
            }
            if (usage.getCompletionTokens() != null) {
                completionTokens.set(usage.getCompletionTokens());
            }
        }

        public Map<String, Object> snapshot() {
            int prompt = promptTokens.get() == null ? estimatedPromptTokens : promptTokens.get();
            int completion = completionTokens.get() == null ? estimateCompletionTokens() : completionTokens.get();
            double seconds = Math.max(0.001d, (System.nanoTime() - startedAt) / 1_000_000_000.0d);
            var metrics = new LinkedHashMap<String, Object>();
            metrics.put("throughputTokensPerSecond", round(completion / seconds));
            metrics.put("promptTokens", prompt);
            metrics.put("completionTokens", completion);
            metrics.put("usedContextTokens", prompt + completion);
            metrics.put("totalContextWindow", maxWindowTokens);
            metrics.put("contextUsageRatio", round((prompt + completion) / (double) maxWindowTokens));
            metrics.put("elapsedMs", Math.round(seconds * 1000));
            return metrics;
        }

        private int estimateCompletionTokens() {
            return Math.max(0, (int) Math.ceil(emittedChars.get() / 4.0d));
        }

        private double round(double value) {
            return Math.round(value * 100.0d) / 100.0d;
        }
    }
}
