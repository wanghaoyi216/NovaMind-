package com.novamind.aigc.react.orchestrator;

import com.novamind.aigc.enums.AgentTypeEnum;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.react.engine.ReasoningEngine;
import com.novamind.aigc.react.engine.ReasoningResult;
import com.novamind.aigc.react.executor.ActionExecutor;
import com.novamind.aigc.react.memory.ConversationMemoryManager;
import com.novamind.aigc.react.model.*;
import com.novamind.aigc.react.registry.ToolRegistry;
import com.novamind.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ReActOrchestrator coordinating the reasoning-action cycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActOrchestratorImpl implements ReActOrchestrator {
    
    private final ReasoningEngine reasoningEngine;
    private final ActionExecutor actionExecutor;
    private final ConversationMemoryManager memoryManager;
    private final ToolRegistry toolRegistry;
    
    private final Map<String, ReActState> activeStates = new ConcurrentHashMap<>();
    
    @Override
    public Flux<ChatEventVO> executeReActLoop(String question, String sessionId, int maxIterations) {
        return Flux.create(sink -> {
            try {
                log.info("Starting ReAct loop: sessionId={}, question={}, maxIterations={}", 
                        sessionId, question, maxIterations);
                
                // Initialize context
                ReActContext context = loadOrCreateContext(sessionId, question);
                
                // Mark as active
                ReActState state = ReActState.builder()
                        .sessionId(sessionId)
                        .active(true)
                        .build();
                activeStates.put(sessionId, state);
                
                // Emit START event
                ChatEventVO startEvent = ChatEventVO.of(
                        ChatEventTypeEnum.DATA,
                        "Starting ReAct process",
                        sessionId,
                        "START"
                );
                sink.next(startEvent);
                
                // Main ReAct loop
                for (int iteration = 1; iteration <= maxIterations; iteration++) {
                    context.setIterationCount(iteration);
                    
                    // Check if should terminate
                    if (reasoningEngine.shouldTerminate(context)) {
                        log.info("ReAct loop terminating early: sessionId={}, iteration={}", 
                                sessionId, iteration);
                        break;
                    }
                    
                    // Phase 1: Reasoning
                    ReasoningResult thought = reasoningEngine.generateThought(context);
                    
                    // Emit THOUGHT event
                    Map<String, Object> thoughtMetadata = new HashMap<>();
                    thoughtMetadata.put("iteration", iteration);
                    ChatEventVO thoughtEvent = ChatEventVO.of(
                            ChatEventTypeEnum.THOUGHT,
                            thought.getThought(),
                            sessionId,
                            "REASONING",
                            thoughtMetadata
                    );
                    sink.next(thoughtEvent);
                    
                    // Phase 2: Action Decision
                    List<Tool> availableTools = toolRegistry.getAvailableTools(
                            context.getAgentType() != null ? context.getAgentType().name() : null
                    );
                    ActionDecision actionDecision = reasoningEngine.decideNextAction(thought, availableTools);
                    
                    // Create step
                    ReActStep step = ReActStep.builder()
                            .stepNumber(iteration)
                            .thought(thought.getThought())
                            .action(actionDecision)
                            .timestamp(System.currentTimeMillis())
                            .status(StepStatus.THINKING)
                            .build();
                    
                    // Handle action
                    if (actionDecision.getType() == ActionType.FINAL_ANSWER) {
                        // Generate final answer
                        String answer = generateFinalAnswer(context, thought);
                        
                        // Emit ANSWER event
                        ChatEventVO answerEvent = ChatEventVO.of(
                                ChatEventTypeEnum.DATA,
                                answer,
                                sessionId,
                                "FINAL"
                        );
                        sink.next(answerEvent);
                        
                        step.setStatus(StepStatus.COMPLETED);
                        context.getSteps().add(step);
                        
                        // Save to memory
                        saveToMemory(sessionId, context, answer);
                        
                        break;
                    }
                    
                    if (actionDecision.getType() == ActionType.USE_TOOL) {
                        // Phase 3: Action Execution
                        step.setStatus(StepStatus.ACTING);
                        
                        ToolCall toolCall = ToolCall.builder()
                                .name(actionDecision.getToolName())
                                .parameters(actionDecision.getParameters())
                                .userId(context.getUserId())
                                .sessionId(sessionId)
                                .build();
                        
                        ActionResult toolResult = actionExecutor.executeTool(toolCall);
                        String observation = actionExecutor.formatObservation(toolResult);
                        
                        // Emit ACTION and OBSERVATION events
                        actionExecutor.streamActionEvents(toolCall, toolResult)
                                .subscribe(sink::next);
                        
                        step.setObservation(observation);
                        step.setStatus(StepStatus.COMPLETED);
                        context.getSteps().add(step);
                    }
                }
                
                // Check if max iterations reached
                if (context.getIterationCount() >= maxIterations) {
                    ChatEventVO errorEvent = ChatEventVO.of(
                            ChatEventTypeEnum.ERROR,
                            "Max iterations reached without final answer",
                            sessionId,
                            "ERROR"
                    );
                    sink.next(errorEvent);
                }
                
                // Save final context
                saveToMemory(sessionId, context, null);
                
                // Emit COMPLETE event
                ChatEventVO completeEvent = ChatEventVO.of(
                        ChatEventTypeEnum.STOP,
                        "ReAct process completed",
                        sessionId,
                        "COMPLETE"
                );
                sink.next(completeEvent);
                
                // Mark as completed
                state.setActive(false);
                state.setCompleted(true);
                
                sink.complete();
                
            } catch (Exception e) {
                log.error("Error in ReAct loop: sessionId={}", sessionId, e);
                
                ChatEventVO errorEvent = ChatEventVO.of(
                        ChatEventTypeEnum.ERROR,
                        "Error: " + e.getMessage(),
                        sessionId,
                        "ERROR"
                );
                sink.next(errorEvent);
                
                // Mark as failed
                ReActState state = activeStates.get(sessionId);
                if (state != null) {
                    state.setActive(false);
                    state.setError(e.getMessage());
                }
                
                sink.error(e);
            }
        });
    }
    
    @Override
    public void stopExecution(String sessionId) {
        ReActState state = activeStates.get(sessionId);
        if (state != null) {
            state.setActive(false);
            log.info("Stopped execution for session: {}", sessionId);
        }
    }
    
    @Override
    public ReActState getState(String sessionId) {
        return activeStates.get(sessionId);
    }
    
    /**
     * Load or create ReAct context
     */
    private ReActContext loadOrCreateContext(String sessionId, String question) {
        // Load conversation history
        List<Message> history = memoryManager.getHistory(sessionId, 4000);
        
        return ReActContext.builder()
                .sessionId(sessionId)
                .currentQuestion(question)
                .conversationHistory(history)
                .agentType(AgentTypeEnum.RECOMMEND) // Default agent type
                .build();
    }
    
    /**
     * Generate final answer from context and thought
     */
    private String generateFinalAnswer(ReActContext context, ReasoningResult thought) {
        // Simple implementation: use the thought as answer
        // In production, this could call LLM again to format a better answer
        return thought.getThought();
    }
    
    /**
     * Save context to memory
     */
    private void saveToMemory(String sessionId, ReActContext context, String answer) {
        try {
            // Save user question
            memoryManager.saveMessage(sessionId, new UserMessage(context.getCurrentQuestion()));
            
            // Save assistant answer if available
            if (answer != null) {
                memoryManager.saveMessage(sessionId, new AssistantMessage(answer));
            }
            
            log.info("Saved conversation to memory: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to save to memory: sessionId={}", sessionId, e);
        }
    }
}
