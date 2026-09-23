package com.novamind.aigc.service;

import com.novamind.aigc.dto.ChatDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "tj.ai.graph", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopUserGraphService implements UserGraphService {

    @Override
    public String loadPromptContext(Long userId, ChatDTO chatDTO) {
        return "";
    }

    @Override
    public void recordConversation(Long userId, ChatDTO chatDTO, String answer) {
        // GraphRAG is disabled.
    }

    @Override
    public Map<String, Object> getGraphVisualization(Long userId) {
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    @Override
    public Map<String, Object> status() {
        return Map.of("enabled", false);
    }
}
