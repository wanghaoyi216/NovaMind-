package com.novamind.aigc.react.memory;

import com.novamind.aigc.memory.MessageUtil;
import com.novamind.aigc.memory.mongodb.ChatRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of ConversationMemoryManager with MongoDB persistence and Redis buffering
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMemoryManagerImpl implements ConversationMemoryManager {
    
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    
    private static final String REDIS_BUFFER_PREFIX = "chat:buffer:";
    private static final int DEFAULT_MAX_TOKENS = 4000;
    private static final int TOKENS_PER_MESSAGE_ESTIMATE = 100;
    
    @Override
    public void saveMessage(String sessionId, Message message) {
        try {
            // Try to save to MongoDB
            Query query = Query.query(Criteria.where("conversationId").is(sessionId));
            ChatRecord chatRecord = mongoTemplate.findOne(query, ChatRecord.class);
            
            if (chatRecord == null) {
                chatRecord = ChatRecord.builder()
                        .conversationId(sessionId)
                        .messages(new ArrayList<>())
                        .build();
            }
            
            chatRecord.getMessages().add(MessageUtil.toJson(message));
            mongoTemplate.save(chatRecord);
            
        } catch (Exception e) {
            log.error("Failed to save message to MongoDB, buffering in Redis", e);
            // Buffer in Redis if MongoDB fails
            String key = REDIS_BUFFER_PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(key, MessageUtil.toJson(message));
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
    }
    
    @Override
    public List<Message> getHistory(String sessionId, int maxTokens) {
        Query query = Query.query(Criteria.where("conversationId").is(sessionId));
        ChatRecord chatRecord = mongoTemplate.findOne(query, ChatRecord.class);
        
        if (chatRecord == null || chatRecord.getMessages() == null || chatRecord.getMessages().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Message> messages = chatRecord.getMessages().stream()
                .map(MessageUtil::toMessage)
                .collect(Collectors.toList());
        
        // Apply context window management
        return applyContextWindow(messages, maxTokens);
    }
    
    @Override
    public void clearSession(String sessionId) {
        Query query = Query.query(Criteria.where("conversationId").is(sessionId));
        mongoTemplate.remove(query, ChatRecord.class);
        
        // Also clear Redis buffer
        String key = REDIS_BUFFER_PREFIX + sessionId;
        redisTemplate.delete(key);
        
        log.info("Cleared session: {}", sessionId);
    }
    
    @Override
    public ConversationSummary summarizeIfNeeded(String sessionId) {
        Query query = Query.query(Criteria.where("conversationId").is(sessionId));
        ChatRecord chatRecord = mongoTemplate.findOne(query, ChatRecord.class);
        
        if (chatRecord == null || chatRecord.getMessages() == null) {
            return null;
        }
        
        int messageCount = chatRecord.getMessages().size();
        int estimatedTokens = messageCount * TOKENS_PER_MESSAGE_ESTIMATE;
        
        if (estimatedTokens > DEFAULT_MAX_TOKENS) {
            // Summarization needed
            log.info("Session {} exceeds token limit, summarization recommended", sessionId);
            
            return ConversationSummary.builder()
                    .sessionId(sessionId)
                    .summary("Conversation history summarized due to length")
                    .messageCount(messageCount)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
        
        return null;
    }
    
    @Override
    public void saveAll(String sessionId, List<Message> messages) {
        ChatRecord chatRecord = ChatRecord.builder()
                .conversationId(sessionId)
                .messages(messages.stream()
                        .map(MessageUtil::toJson)
                        .collect(Collectors.toList()))
                .build();
        
        mongoTemplate.save(chatRecord);
        log.info("Saved {} messages for session {}", messages.size(), sessionId);
    }
    
    /**
     * Apply context window management to keep messages within token limit
     */
    private List<Message> applyContextWindow(List<Message> messages, int maxTokens) {
        if (messages.isEmpty()) {
            return messages;
        }
        
        // Always include system message if present
        Message systemMessage = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .findFirst()
                .orElse(null);
        
        List<Message> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        
        // Add messages from most recent, working backwards
        List<Message> nonSystemMessages = messages.stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                .collect(Collectors.toList());
        
        Collections.reverse(nonSystemMessages);
        
        int currentTokens = systemMessage != null ? TOKENS_PER_MESSAGE_ESTIMATE : 0;
        List<Message> recentMessages = new ArrayList<>();
        
        for (Message message : nonSystemMessages) {
            int messageTokens = estimateTokens(message);
            
            if (currentTokens + messageTokens <= maxTokens) {
                recentMessages.add(message);
                currentTokens += messageTokens;
            } else {
                break;
            }
        }
        
        // Reverse back to chronological order
        Collections.reverse(recentMessages);
        result.addAll(recentMessages);
        
        return result;
    }
    
    /**
     * Estimate token count for a message
     */
    private int estimateTokens(Message message) {
        if (message.getText() == null) {
            return 0;
        }
        // Rough estimate: 1 token ≈ 4 characters
        return message.getText().length() / 4;
    }
}
