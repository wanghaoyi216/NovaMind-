package com.novamind.aigc.react.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of tool execution
 */
@Data
@Builder
public class ActionResult {
    /**
     * Whether the execution was successful
     */
    private boolean success;
    
    /**
     * Result data from tool execution
     */
    private Object data;
    
    /**
     * Error message if execution failed
     */
    private String error;
    
    /**
     * Execution time in milliseconds
     */
    private Long executionTime;
    
    /**
     * Create a successful result
     */
    public static ActionResult success(Object data, long executionTime) {
        return ActionResult.builder()
                .success(true)
                .data(data)
                .executionTime(executionTime)
                .build();
    }
    
    /**
     * Create a failed result
     */
    public static ActionResult failure(String error) {
        return ActionResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
