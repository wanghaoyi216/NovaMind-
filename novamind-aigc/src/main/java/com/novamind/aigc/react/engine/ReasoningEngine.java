package com.novamind.aigc.react.engine;

import com.novamind.aigc.react.model.ActionDecision;
import com.novamind.aigc.react.model.ReActContext;
import com.novamind.aigc.react.model.Tool;

import java.util.List;

/**
 * Engine for generating reasoning traces and determining next actions
 */
public interface ReasoningEngine {
    /**
     * Generate a thought based on current context
     * @param context ReAct execution context
     * @return Reasoning result with thought text
     */
    ReasoningResult generateThought(ReActContext context);
    
    /**
     * Decide the next action based on reasoning result
     * @param thought Reasoning result
     * @param availableTools List of available tools
     * @return Action decision
     */
    ActionDecision decideNextAction(ReasoningResult thought, List<Tool> availableTools);
    
    /**
     * Check if execution should terminate
     * @param context ReAct execution context
     * @return true if should terminate
     */
    boolean shouldTerminate(ReActContext context);
}
