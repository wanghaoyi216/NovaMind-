package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.context")
public class ContextWindowProperties {

    /**
     * Total model context window used for local budget estimation.
     */
    private int maxWindowTokens = 8192;

    /**
     * Trigger semantic compaction when used context reaches this ratio.
     */
    private double compactionThresholdRatio = 0.70d;

    /**
     * Keep the latest messages verbatim after compaction.
     */
    private int retainRecentMessages = 20;

    /**
     * Avoid compacting short conversations.
     */
    private int minMessagesBeforeCompaction = 24;

    /**
     * Upper bound for source transcript sent to the summarizer.
     */
    private int maxSummarySourceTokens = 12000;

    /**
     * Coarse estimator. CJK-heavy text is adjusted in ContextTokenEstimator.
     */
    private int estimatedCharsPerToken = 4;

    private boolean compactionEnabled = true;

    private String summarySystemPrompt = """
            You are a conversation memory compactor. Create a concise semantic summary that preserves:
            user goals, hard constraints, important entities, decisions, tool calls, observation results,
            unresolved tasks, and preferences. Do not invent facts. Reply in the same primary language as
            the transcript.
            """;
}
