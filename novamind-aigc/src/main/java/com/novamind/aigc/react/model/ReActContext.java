package com.novamind.aigc.react.model;

import com.novamind.aigc.enums.AgentTypeEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct execution context containing session state, conversation history, and execution metadata
 */
@Data
@Builder
public class ReActContext {
    /**
     * Unique session identifier
     */
    private String sessionId;
    
    /**
     * User identifier
     */
    private String userId;
    
    /**
     * Current question being processed
     */
    private String currentQuestion;
    
    /**
     * Conversation history (Spring AI Message objects)
     */
    @Builder.Default
    private List<Message> conversationHistory = new ArrayList<>();
    
    /**
     * ReAct execution steps
     */
    @Builder.Default
    private List<ReActStep> steps = new ArrayList<>();
    
    /**
     * Current iteration count
     */
    @Builder.Default
    private int iterationCount = 0;
    
    /**
     * Agent type for this context
     */
    private AgentTypeEnum agentType;
    
    /**
     * Additional metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
