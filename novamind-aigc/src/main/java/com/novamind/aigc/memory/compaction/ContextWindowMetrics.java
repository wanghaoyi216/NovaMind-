package com.novamind.aigc.memory.compaction;

public record ContextWindowMetrics(
        int usedTokens,
        int maxWindowTokens,
        double usedRatio,
        int messageCount
) {

    public boolean shouldCompact(double thresholdRatio, int minMessagesBeforeCompaction) {
        return messageCount >= minMessagesBeforeCompaction && usedRatio >= thresholdRatio;
    }
}
