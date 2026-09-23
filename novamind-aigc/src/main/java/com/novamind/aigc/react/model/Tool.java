package com.novamind.aigc.react.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.Map;

/**
 * Tool definition for ReAct system
 */
@Data
@Builder
public class Tool {
    /**
     * Unique tool name
     */
    private String name;
    
    /**
     * Tool description for LLM
     */
    private String description;
    
    /**
     * Tool parameters
     */
    @Builder.Default
    private List<ToolParameter> parameters = new ArrayList<>();
    
    /**
     * Service endpoint (for Feign client routing)
     */
    private String serviceEndpoint;
    
    /**
     * Tool category
     */
    private ToolCategory category;
    
    /**
     * Whether this tool requires authentication
     */
    @Builder.Default
    private boolean requiresAuth = true;
    
    /**
     * Tool executor function
     */
    private Function<Map<String, Object>, Object> executor;
}
