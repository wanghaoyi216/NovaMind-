package com.novamind.aigc.react.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an action decision made by the reasoning engine
 */
@Data
@Builder
public class ActionDecision {
    /**
     * Type of action to take
     */
    private ActionType type;
    
    /**
     * Name of the tool to invoke (for USE_TOOL actions)
     */
    private String toolName;
    
    /**
     * Parameters for the tool invocation
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();
    
    /**
     * Reasoning behind this action decision
     */
    private String reasoning;
}
