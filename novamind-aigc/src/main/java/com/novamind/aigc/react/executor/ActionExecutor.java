package com.novamind.aigc.react.executor;

import com.novamind.aigc.react.model.ActionResult;
import com.novamind.aigc.react.model.ToolCall;
import com.novamind.aigc.vo.ChatEventVO;
import reactor.core.publisher.Flux;

/**
 * Executor for tool actions in ReAct cycle
 */
public interface ActionExecutor {
    /**
     * Execute a tool call
     * @param toolCall Tool call with name and parameters
     * @return Action result
     */
    ActionResult executeTool(ToolCall toolCall);
    
    /**
     * Format observation from action result
     * @param result Action result
     * @return Formatted observation string
     */
    String formatObservation(ActionResult result);
    
    /**
     * Stream action events to frontend
     * @param toolCall Tool call
     * @param result Action result
     * @return Flux of chat events
     */
    Flux<ChatEventVO> streamActionEvents(ToolCall toolCall, ActionResult result);
}
