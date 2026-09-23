package com.novamind.aigc.react.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tool invocation request
 */
@Data
@Builder
public class ToolCall {
    /**
     * Tool name to invoke
     */
    private String name;
    
    /**
     * Parameters for the tool
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();
    
    /**
     * User ID making the call (for permission checking)
     */
    private String userId;
    
    /**
     * Session ID for context
     */
    private String sessionId;
}
