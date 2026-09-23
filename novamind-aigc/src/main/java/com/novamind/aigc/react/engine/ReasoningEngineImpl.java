package com.novamind.aigc.react.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novamind.aigc.react.model.*;
import com.novamind.aigc.react.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of ReasoningEngine using Spring AI Alibaba
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReasoningEngineImpl implements ReasoningEngine {
    
    private final ChatClient.Builder chatClientBuilder;
    private final ToolRegistry toolRegistry;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String REDIS_CACHE_PREFIX = "react:reasoning:";
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_BACKOFF_MS = 1000;
    
    @Override
    public ReasoningResult generateThought(ReActContext context) {
        // Check cache first
        String cacheKey = REDIS_CACHE_PREFIX + generateCacheKey(context);
        String cachedThought = redisTemplate.opsForValue().get(cacheKey);
        if (cachedThought != null) {
            log.debug("Using cached reasoning for session {}", context.getSessionId());
            return ReasoningResult.builder()
                    .thought(cachedThought)
                    .rawResponse(cachedThought)
                    .build();
        }
        
        // Build ReAct prompt
        String prompt = buildReActPrompt(context);
        
        // Call LLM with retry logic
        String response = callLLMWithRetry(prompt);
        
        // Extract thought from response
        String thought = extractThought(response);
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, thought, 1, TimeUnit.HOURS);
        
        return ReasoningResult.builder()
                .thought(thought)
                .rawResponse(response)
                .build();
    }
    
    @Override
    public ActionDecision decideNextAction(ReasoningResult thought, List<Tool> availableTools) {
        String thoughtText = thought.getThought().toLowerCase();
        
        // Parse thought to determine action type
        if (thoughtText.contains("final answer") || thoughtText.contains("conclude")) {
            return ActionDecision.builder()
                    .type(ActionType.FINAL_ANSWER)
                    .reasoning(thought.getThought())
                    .build();
        }
        
        if (thoughtText.contains("need more information") || thoughtText.contains("clarify")) {
            return ActionDecision.builder()
                    .type(ActionType.ASK_CLARIFICATION)
                    .reasoning(thought.getThought())
                    .build();
        }
        
        // Try to extract tool name and parameters from thought
        for (Tool tool : availableTools) {
            if (thoughtText.contains(tool.getName().toLowerCase())) {
                Map<String, Object> parameters = extractParameters(thought.getThought(), tool);
                return ActionDecision.builder()
                        .type(ActionType.USE_TOOL)
                        .toolName(tool.getName())
                        .parameters(parameters)
                        .reasoning(thought.getThought())
                        .build();
            }
        }
        
        // Default: provide final answer
        return ActionDecision.builder()
                .type(ActionType.FINAL_ANSWER)
                .reasoning(thought.getThought())
                .build();
    }
    
    @Override
    public boolean shouldTerminate(ReActContext context) {
        // Terminate if max iterations reached
        if (context.getIterationCount() >= 10) {
            log.warn("Max iterations reached for session {}", context.getSessionId());
            return true;
        }
        
        // Detect infinite loop: same action repeated 3 times
        if (context.getSteps().size() >= 3) {
            List<ReActStep> recentSteps = context.getSteps().subList(
                    Math.max(0, context.getSteps().size() - 3),
                    context.getSteps().size()
            );
            
            boolean allSameAction = recentSteps.stream()
                    .map(step -> step.getAction() != null ? step.getAction().getToolName() : null)
                    .distinct()
                    .count() == 1;
            
            if (allSameAction) {
                log.warn("Infinite loop detected for session {}", context.getSessionId());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Build ReAct prompt with conversation history and tool schemas
     */
    private String buildReActPrompt(ReActContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // Add system instructions
        prompt.append("You are a helpful AI assistant using the ReAct (Reasoning + Acting) framework.\n");
        prompt.append("For each step, you should:\n");
        prompt.append("1. Think: Analyze the situation and decide what to do next\n");
        prompt.append("2. Act: Either use a tool or provide a final answer\n\n");
        
        // Add available tools
        String toolSchema = toolRegistry.generateToolSchema(
                context.getAgentType() != null ? context.getAgentType().name() : null
        );
        prompt.append("Available tools:\n").append(toolSchema).append("\n\n");
        
        // Add previous steps
        for (ReActStep step : context.getSteps()) {
            prompt.append("Thought: ").append(step.getThought()).append("\n");
            if (step.getAction() != null) {
                prompt.append("Action: ").append(step.getAction().getToolName()).append("\n");
            }
            if (step.getObservation() != null) {
                prompt.append("Observation: ").append(step.getObservation()).append("\n");
            }
        }
        
        // Add current question
        prompt.append("Question: ").append(context.getCurrentQuestion()).append("\n");
        prompt.append("Thought: ");
        
        return prompt.toString();
    }
    
    /**
     * Call LLM with exponential backoff retry
     */
    private String callLLMWithRetry(String prompt) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                ChatClient chatClient = chatClientBuilder.build();
                
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage("You are a helpful AI assistant using ReAct framework."));
                messages.add(new UserMessage(prompt));
                
                String response = chatClient.prompt(new Prompt(messages))
                        .call()
                        .content();
                
                return response;
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt < MAX_RETRIES) {
                    int backoffMs = INITIAL_BACKOFF_MS * (int) Math.pow(2, attempt - 1);
                    log.warn("LLM call failed (attempt {}/{}), retrying in {}ms", 
                            attempt, MAX_RETRIES, backoffMs, e);
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("LLM call failed after {} attempts", MAX_RETRIES, lastException);
        return "I'm having trouble thinking right now. Let me try a simpler approach.";
    }
    
    /**
     * Extract thought from LLM response
     */
    private String extractThought(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "No thought generated";
        }
        
        // Simple extraction: take first line or paragraph
        String[] lines = response.split("\n");
        return lines[0].trim();
    }
    
    /**
     * Extract parameters from thought text for a specific tool
     */
    private Map<String, Object> extractParameters(String thought, Tool tool) {
        Map<String, Object> parameters = new HashMap<>();
        
        // Simple parameter extraction (can be enhanced with NLP)
        for (ToolParameter param : tool.getParameters()) {
            // Try to find parameter value in thought text
            String paramName = param.getName();
            // This is a simplified implementation
            // In production, use more sophisticated NLP or structured output from LLM
            parameters.put(paramName, "");
        }
        
        return parameters;
    }
    
    /**
     * Generate cache key for reasoning result
     */
    private String generateCacheKey(ReActContext context) {
        // Simple cache key based on question and step count
        return context.getCurrentQuestion().hashCode() + ":" + context.getSteps().size();
    }
}
