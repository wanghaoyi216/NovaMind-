package com.novamind.aigc.react.model;

/**
 * Type of action in ReAct cycle
 */
public enum ActionType {
    /**
     * Use a tool to gather information
     */
    USE_TOOL,
    
    /**
     * Provide final answer to the user
     */
    FINAL_ANSWER,
    
    /**
     * Ask user for clarification
     */
    ASK_CLARIFICATION,
    
    /**
     * Delegate to a specialized agent
     */
    DELEGATE_TO_AGENT
}
