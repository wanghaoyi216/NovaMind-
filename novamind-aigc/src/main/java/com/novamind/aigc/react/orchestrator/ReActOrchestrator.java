package com.novamind.aigc.react.orchestrator;

import com.novamind.aigc.vo.ChatEventVO;
import reactor.core.publisher.Flux;

/**
 * Orchestrator for ReAct (Reasoning + Acting) execution loop
 */
public interface ReActOrchestrator {
    /**
     * Execute ReAct loop for a question
     * @param question User question
     * @param sessionId Session identifier
     * @param maxIterations Maximum iterations allowed
     * @return Flux of chat events
     */
    Flux<ChatEventVO> executeReActLoop(String question, String sessionId, int maxIterations);
    
    /**
     * Stop execution for a session
     * @param sessionId Session identifier
     */
    void stopExecution(String sessionId);
    
    /**
     * Get current execution state
     * @param sessionId Session identifier
     * @return Execution state
     */
    ReActState getState(String sessionId);
}
