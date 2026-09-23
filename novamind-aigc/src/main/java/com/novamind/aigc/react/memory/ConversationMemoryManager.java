package com.novamind.aigc.react.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Manager for conversation memory with context window management
 */
public interface ConversationMemoryManager {
    /**
     * Save a message to the conversation history
     * @param sessionId Session identifier
     * @param message Message to save
     */
    void saveMessage(String sessionId, Message message);
    
    /**
     * Get conversation history within token limit
     * @param sessionId Session identifier
     * @param maxTokens Maximum tokens allowed
     * @return List of messages within token limit
     */
    List<Message> getHistory(String sessionId, int maxTokens);
    
    /**
     * Clear all messages for a session
     * @param sessionId Session identifier
     */
    void clearSession(String sessionId);
    
    /**
     * Summarize conversation if needed (when context exceeds limits)
     * @param sessionId Session identifier
     * @return Summary message if summarization occurred
     */
    ConversationSummary summarizeIfNeeded(String sessionId);
    
    /**
     * Save all messages in a batch
     * @param sessionId Session identifier
     * @param messages Messages to save
     */
    void saveAll(String sessionId, List<Message> messages);
}
