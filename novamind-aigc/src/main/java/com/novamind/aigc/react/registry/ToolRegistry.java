package com.novamind.aigc.react.registry;

import com.novamind.aigc.react.model.Tool;

import java.util.List;
import java.util.Optional;

/**
 * Registry for managing available tools in the ReAct system
 */
public interface ToolRegistry {
    /**
     * Register a new tool
     * @param tool Tool to register
     */
    void registerTool(Tool tool);
    
    /**
     * Get all available tools for a specific agent type
     * @param agentType Agent type (null for all tools)
     * @return List of available tools
     */
    List<Tool> getAvailableTools(String agentType);
    
    /**
     * Get a specific tool by name
     * @param toolName Tool name
     * @return Optional containing the tool if found
     */
    Optional<Tool> getTool(String toolName);
    
    /**
     * Generate JSON schema for all tools (for LLM function calling)
     * @return JSON schema string
     */
    String generateToolSchema();
    
    /**
     * Generate JSON schema for tools available to a specific agent type
     * @param agentType Agent type
     * @return JSON schema string
     */
    String generateToolSchema(String agentType);
}
