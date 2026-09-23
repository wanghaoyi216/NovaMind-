package com.novamind.aigc.service;

import com.novamind.aigc.dto.ChatDTO;

import java.util.List;
import java.util.Map;

public interface UserGraphService {

    String loadPromptContext(Long userId, ChatDTO chatDTO);

    void recordConversation(Long userId, ChatDTO chatDTO, String answer);

    Map<String, Object> status();

    Map<String, Object> getGraphVisualization(Long userId);
}
