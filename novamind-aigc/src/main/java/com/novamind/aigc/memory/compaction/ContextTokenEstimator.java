package com.novamind.aigc.memory.compaction;

import com.novamind.aigc.config.ContextWindowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ContextTokenEstimator {

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private final ContextWindowProperties properties;

    public ContextWindowMetrics estimate(List<Message> messages) {
        int usedTokens = 0;
        if (messages != null) {
            for (Message message : messages) {
                usedTokens += MESSAGE_OVERHEAD_TOKENS;
                usedTokens += estimate(message == null ? null : message.getText());
            }
        }
        int maxTokens = Math.max(1, properties.getMaxWindowTokens());
        return new ContextWindowMetrics(
                usedTokens,
                maxTokens,
                usedTokens / (double) maxTokens,
                messages == null ? 0 : messages.size()
        );
    }

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkChars = 0;
        int nonCjkChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjkChars++;
            } else {
                nonCjkChars++;
            }
        }
        int estimatedCharsPerToken = Math.max(1, properties.getEstimatedCharsPerToken());
        int cjkTokens = (int) Math.ceil(cjkChars * 0.75d);
        int nonCjkTokens = (int) Math.ceil(nonCjkChars / (double) estimatedCharsPerToken);
        return Math.max(1, cjkTokens + nonCjkTokens);
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
