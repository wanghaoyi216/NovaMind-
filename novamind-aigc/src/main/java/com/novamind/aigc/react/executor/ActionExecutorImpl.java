package com.novamind.aigc.react.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.react.model.*;
import com.novamind.aigc.react.registry.ToolRegistry;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.exceptions.BadRequestException;
import com.novamind.common.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Implementation of ActionExecutor with tool validation and execution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutorImpl implements ActionExecutor {
    
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final int TOOL_TIMEOUT_SECONDS = 30;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Override
    public ActionResult executeTool(ToolCall toolCall) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Validate tool exists
            Optional<Tool> toolOpt = toolRegistry.getTool(toolCall.getName());
            if (toolOpt.isEmpty()) {
                log.error("Tool not found: {}", toolCall.getName());
                return ActionResult.failure("Tool not found: " + toolCall.getName());
            }
            
            Tool tool = toolOpt.get();
            
            // 2. Validate parameters
            String validationError = validateParameters(tool, toolCall.getParameters());
            if (validationError != null) {
                log.error("Parameter validation failed for tool {}: {}", toolCall.getName(), validationError);
                return ActionResult.failure(validationError);
            }
            
            // 3. Check permissions
            if (tool.isRequiresAuth() && !hasPermission(toolCall.getUserId(), tool)) {
                log.error("Permission denied for user {} to use tool {}", toolCall.getUserId(), toolCall.getName());
                return ActionResult.failure("Permission denied");
            }
            
            // 4. Execute tool with timeout
            Future<Object> future = executorService.submit(() -> {
                try {
                    return tool.getExecutor().apply(toolCall.getParameters());
                } catch (Exception e) {
                    log.error("Tool execution error: {}", toolCall.getName(), e);
                    throw e;
                }
            });
            
            Object result;
            try {
                result = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.error("Tool execution timeout: {}", toolCall.getName());
                return ActionResult.failure("Tool execution timeout");
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 5. Log execution
            logToolExecution(toolCall, result, executionTime);
            
            return ActionResult.success(result, executionTime);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Tool execution interrupted: {}", toolCall.getName(), e);
            return ActionResult.failure("Tool execution interrupted");
        } catch (ExecutionException e) {
            log.error("Tool execution failed: {}", toolCall.getName(), e.getCause());
            return ActionResult.failure("Tool execution failed: " + e.getCause().getMessage());
        } catch (Exception e) {
            log.error("Unexpected error executing tool: {}", toolCall.getName(), e);
            return ActionResult.failure("Unexpected error: " + e.getMessage());
        }
    }
    
    @Override
    public String formatObservation(ActionResult result) {
        if (!result.isSuccess()) {
            return "Error: " + result.getError();
        }
        
        try {
            // Format result as JSON string
            if (result.getData() == null) {
                return "Tool executed successfully with no return value";
            }
            
            if (result.getData() instanceof String) {
                return (String) result.getData();
            }
            
            return objectMapper.writeValueAsString(result.getData());
        } catch (Exception e) {
            log.error("Failed to format observation", e);
            return "Result: " + result.getData().toString();
        }
    }
    
    @Override
    public Flux<ChatEventVO> streamActionEvents(ToolCall toolCall, ActionResult result) {
        return Flux.create(sink -> {
            try {
                // Emit ACTION event
                Map<String, Object> actionMetadata = new HashMap<>();
                actionMetadata.put("toolName", toolCall.getName());
                actionMetadata.put("parameters", toolCall.getParameters());
                
                ChatEventVO actionEvent = ChatEventVO.of(
                        ChatEventTypeEnum.ACTION,
                        "Executing tool: " + toolCall.getName(),
                        toolCall.getSessionId(),
                        "TOOL",
                        actionMetadata
                );
                sink.next(actionEvent);
                
                // Emit OBSERVATION event
                Map<String, Object> observationMetadata = new HashMap<>();
                observationMetadata.put("success", result.isSuccess());
                observationMetadata.put("executionTime", result.getExecutionTime());
                
                String observation = formatObservation(result);
                ChatEventVO observationEvent = ChatEventVO.of(
                        ChatEventTypeEnum.OBSERVATION,
                        observation,
                        toolCall.getSessionId(),
                        "TOOL",
                        observationMetadata
                );
                sink.next(observationEvent);
                
                sink.complete();
            } catch (Exception e) {
                log.error("Error streaming action events", e);
                sink.error(e);
            }
        });
    }
    
    /**
     * Validate tool parameters against schema
     */
    private String validateParameters(Tool tool, Map<String, Object> parameters) {
        for (ToolParameter param : tool.getParameters()) {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                return "Missing required parameter: " + param.getName();
            }
            
            if (parameters.containsKey(param.getName())) {
                Object value = parameters.get(param.getName());
                // Basic type validation
                if (!validateParameterType(value, param.getType())) {
                    return "Invalid type for parameter " + param.getName() + 
                           ", expected " + param.getType();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Validate parameter type
     */
    private boolean validateParameterType(Object value, String expectedType) {
        if (value == null) {
            return true;
        }
        
        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "integer", "int" -> value instanceof Integer || value instanceof Long;
            case "number", "double" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof java.util.Collection;
            case "object" -> value instanceof Map;
            default -> true; // Unknown type, allow it
        };
    }
    
    /**
     * Check if user has permission to use tool
     */
    private boolean hasPermission(String userId, Tool tool) {
        // Basic permission check
        // In production, integrate with novamind-auth-gateway-sdk for RBAC
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        
        // For now, allow all authenticated users
        // TODO: Implement proper RBAC permission checking
        return true;
    }
    
    /**
     * Log tool execution for monitoring
     */
    private void logToolExecution(ToolCall toolCall, Object result, long executionTime) {
        log.info("Tool executed: name={}, user={}, session={}, executionTime={}ms, success=true",
                toolCall.getName(),
                toolCall.getUserId(),
                toolCall.getSessionId(),
                executionTime);
    }
}
