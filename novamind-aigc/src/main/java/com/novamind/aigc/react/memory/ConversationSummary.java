package com.novamind.aigc.react.memory;

import lombok.Builder;
import lombok.Data;

/**
 * Summary of a conversation when context window is exceeded
 */
@Data
@Builder
public class ConversationSummary {
    /**
     * Session identifier
     */
    private String sessionId;
    
    /**
     * Summary text
     */
    private String summary;
    
    /**
     * Number of messages summarized
     */
    private int messageCount;
    
    /**
     * Timestamp of summarization
     */
    private long timestamp;
}
