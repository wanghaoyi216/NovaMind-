package com.novamind.aigc.react.engine;

import lombok.Builder;
import lombok.Data;

/**
 * Result of reasoning generation
 */
@Data
@Builder
public class ReasoningResult {
    /**
     * Generated thought text
     */
    private String thought;
    
    /**
     * Raw response from LLM
     */
    private String rawResponse;
    
    /**
     * Confidence score (0-1)
     */
    private Double confidence;
}
