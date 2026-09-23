package com.novamind.aigc.react.model;

/**
 * Status of a ReAct execution step
 */
public enum StepStatus {
    /**
     * Currently generating thought
     */
    THINKING,
    
    /**
     * Currently executing action
     */
    ACTING,
    
    /**
     * Currently processing observation
     */
    OBSERVING,
    
    /**
     * Step completed successfully
     */
    COMPLETED,
    
    /**
     * Step failed with error
     */
    FAILED
}
