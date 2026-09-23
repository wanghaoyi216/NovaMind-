package com.novamind.aigc.react.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.novamind.aigc.react.model.Tool;
import com.novamind.aigc.react.model.ToolParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of ToolRegistry with in-memory storage and caching
 */
@Slf4j
@Component
public class ToolRegistryImpl implements ToolRegistry {
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Cache<String, String> schemaCache;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ToolRegistryImpl() {
        // Initialize Caffeine cache for tool schemas
        this.schemaCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }
    
    @Override
    public void registerTool(Tool tool) {
        if (tool == null || tool.getName() == null) {
            throw new IllegalArgumentException("Tool and tool name cannot be null");
        }
        
        if (tools.containsKey(tool.getName())) {
            log.warn("Tool {} already registered, replacing with new definition", tool.getName());
        }
        
        tools.put(tool.getName(), tool);
        // Invalidate schema cache when tools change
        schemaCache.invalidateAll();
        
        log.info("Registered tool: {} (category: {}, requiresAuth: {})", 
                tool.getName(), tool.getCategory(), tool.isRequiresAuth());
    }
    
    @Override
    public List<Tool> getAvailableTools(String agentType) {
        if (agentType == null) {
            return new ArrayList<>(tools.values());
        }
        
        // Filter tools by agent type (can be extended with more sophisticated logic)
        return tools.values().stream()
                .filter(tool -> isToolAvailableForAgent(tool, agentType))
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Tool> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }
    
    @Override
    public String generateToolSchema() {
        return schemaCache.get("all", key -> buildToolSchema(getAvailableTools(null)));
    }
    
    @Override
    public String generateToolSchema(String agentType) {
        return schemaCache.get("agent_" + agentType, 
                key -> buildToolSchema(getAvailableTools(agentType)));
    }
    
    /**
     * Check if a tool is available for a specific agent type
     */
    private boolean isToolAvailableForAgent(Tool tool, String agentType) {
        // Default: all tools available to all agents
        // Can be extended with agent-specific tool filtering logic
        return true;
    }
    
    /**
     * Build JSON schema for tools (OpenAI function calling format)
     */
    private String buildToolSchema(List<Tool> toolList) {
        try {
            List<Map<String, Object>> functions = new ArrayList<>();
            
            for (Tool tool : toolList) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription());
                
                // Build parameters schema
                Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("type", "object");
                
                Map<String, Object> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                
                for (ToolParameter param : tool.getParameters()) {
                    Map<String, Object> paramSchema = new LinkedHashMap<>();
                    paramSchema.put("type", param.getType());
                    paramSchema.put("description", param.getDescription());
                    
                    if (param.getDefaultValue() != null) {
                        paramSchema.put("default", param.getDefaultValue());
                    }
                    
                    properties.put(param.getName(), paramSchema);
                    
                    if (param.isRequired()) {
                        required.add(param.getName());
                    }
                }
                
                parameters.put("properties", properties);
                if (!required.isEmpty()) {
                    parameters.put("required", required);
                }
                
                function.put("parameters", parameters);
                functions.add(function);
            }
            
            return objectMapper.writeValueAsString(functions);
        } catch (Exception e) {
            log.error("Failed to generate tool schema", e);
            return "[]";
        }
    }
}
