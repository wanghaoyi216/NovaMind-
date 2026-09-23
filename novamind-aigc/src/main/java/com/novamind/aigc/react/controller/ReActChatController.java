package com.novamind.aigc.react.controller;

import com.novamind.aigc.memory.mongodb.ChatRecord;
import com.novamind.aigc.react.dto.ChatRequest;
import com.novamind.aigc.react.memory.ConversationMemoryManager;
import com.novamind.aigc.react.orchestrator.ReActOrchestrator;
import com.novamind.aigc.react.orchestrator.ReActState;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.common.domain.R;
import com.novamind.common.exceptions.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * ReAct Chat Controller for AI interactions with SSE streaming
 */
@Slf4j
@RestController
@RequestMapping("/react/chat")
@RequiredArgsConstructor
@Tag(name = "ReAct Chat API", description = "AI Chat with ReAct (Reasoning + Acting) architecture")
public class ReActChatController {
    
    private final ReActOrchestrator reactOrchestrator;
    private final ConversationMemoryManager memoryManager;
    private final MongoTemplate mongoTemplate;
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream chat with ReAct", description = "Send a question and receive streaming ReAct events (thoughts, actions, observations, answer)")
    public Flux<ServerSentEvent<ChatEventVO>> chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Received chat stream request: sessionId={}, question={}, userId={}", 
                request.getSessionId(), request.getQuestion(), userId);
        
        // Generate session ID if not provided
        String tempSessionId = request.getSessionId();
        if (tempSessionId == null || tempSessionId.isEmpty()) {
            tempSessionId = UUID.randomUUID().toString();
        }
        final String sessionId = tempSessionId;
        
        // Validate request
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("Question cannot be empty"));
        }
        
        int maxIterations = request.getMaxIterations() != null ? request.getMaxIterations() : 10;
        
        // Use user-specific conversation ID to ensure data isolation
        final String conversationId = userId != null ? userId + "_" + sessionId : sessionId;
        
        // Execute ReAct loop and convert to SSE
        return reactOrchestrator.executeReActLoop(request.getQuestion(), conversationId, maxIterations)
                .map(event -> ServerSentEvent.<ChatEventVO>builder()
                        .id(UUID.randomUUID().toString())
                        .event(event.getEventName())
                        .data(event)
                        .build())
                .doOnError(error -> log.error("Error in chat stream: conversationId={}", conversationId, error))
                .onErrorResume(error -> {
                    // Send error event
                    ChatEventVO errorEvent = ChatEventVO.builder()
                            .eventType(1002) // ERROR type
                            .eventName("ERROR")
                            .eventData("Error: " + error.getMessage())
                            .requestId(sessionId)
                            .phase("ERROR")
                            .build();
                    
                    return Flux.just(ServerSentEvent.<ChatEventVO>builder()
                            .event("ERROR")
                            .data(errorEvent)
                            .build());
                })
                .doOnComplete(() -> log.info("Chat stream completed: conversationId={}", conversationId));
    }
    
    @GetMapping("/sessions")
    @Operation(summary = "List user sessions", description = "Get all chat sessions for the current user")
    public R<List<String>> listSessions(
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Listing sessions for user: {}", userId);
        
        if (userId == null || userId.isEmpty()) {
            return R.ok(List.of());
        }
        
        // Query chat records from MongoDB starting with "userId_"
        Query query = Query.query(Criteria.where("conversationId").regex("^" + userId + "_"));
        List<ChatRecord> records = mongoTemplate.find(query, ChatRecord.class);
        
        // Extract the original sessionId (uuid part) by removing the "userId_" prefix
        List<String> sessionIds = records.stream()
                .map(ChatRecord::getConversationId)
                .map(convId -> convId.substring(userId.length() + 1))
                .toList();
        
        return R.ok(sessionIds);
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Clear session", description = "Delete all messages for a session")
    public R<Void> clearSession(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Clearing session: sessionId={}, userId={}", sessionId, userId);
        
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("Authentication required");
        }
        
        String conversationId = userId + "_" + sessionId;
        
        // Security check - Verify the session belongs to the current user
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        boolean exists = mongoTemplate.exists(query, ChatRecord.class);
        if (!exists) {
            log.warn("Unauthorized attempt to clear session or session not found: conversationId={}", conversationId);
            throw new ForbiddenException("Access denied to session");
        }
        
        memoryManager.clearSession(conversationId);
        
        return R.ok();
    }
    
    @GetMapping("/sessions/{sessionId}/history")
    @Operation(summary = "Get session history", description = "Retrieve conversation history for a session")
    public R<List<Message>> getHistory(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @Parameter(description = "Max tokens") @RequestParam(defaultValue = "4000") int maxTokens,
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Getting history: sessionId={}, userId={}, maxTokens={}", sessionId, userId, maxTokens);
        
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("Authentication required");
        }
        
        String conversationId = userId + "_" + sessionId;
        
        // Security check - Verify the session belongs to the current user
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        boolean exists = mongoTemplate.exists(query, ChatRecord.class);
        if (!exists) {
            log.warn("Unauthorized attempt to access history or session not found: conversationId={}", conversationId);
            throw new ForbiddenException("Access denied to session");
        }
        
        List<Message> history = memoryManager.getHistory(conversationId, maxTokens);
        
        return R.ok(history);
    }
    
    @GetMapping("/sessions/{sessionId}/state")
    @Operation(summary = "Get execution state", description = "Get current ReAct execution state for a session")
    public R<ReActState> getState(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Getting state: sessionId={}, userId={}", sessionId, userId);
        
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("Authentication required");
        }
        
        String conversationId = userId + "_" + sessionId;
        
        ReActState state = reactOrchestrator.getState(conversationId);
        
        return R.ok(state);
    }
    
    @PostMapping("/sessions/{sessionId}/stop")
    @Operation(summary = "Stop execution", description = "Stop ReAct execution for a session")
    public R<Void> stopExecution(
            @Parameter(description = "Session ID") @PathVariable String sessionId,
            @RequestHeader(value = "user-info", required = false) String userId) {
        
        log.info("Stopping execution: sessionId={}, userId={}", sessionId, userId);
        
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("Authentication required");
        }
        
        String conversationId = userId + "_" + sessionId;
        
        reactOrchestrator.stopExecution(conversationId);
        
        return R.ok();
    }
}
