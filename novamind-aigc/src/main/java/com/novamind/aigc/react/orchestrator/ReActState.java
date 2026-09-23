package com.novamind.aigc.react.orchestrator;

import com.novamind.aigc.react.model.ReActStep;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Current state of ReAct execution
 */
@Data
@Builder
public class ReActState {
    /**
     * Session identifier
     */
    private String sessionId;
    
    /**
     * Current iteration count
     */
    private int iterationCount;
    
    /**
     * Execution steps
     */
    @Builder.Default
    private List<ReActStep> steps = new ArrayList<>();
    
    /**
     * Whether execution is active
     */
    private boolean active;
    
    /**
     * Whether execution completed successfully
     */
    private boolean completed;
    
    /**
     * Error message if failed
     */
    private String error;
}
