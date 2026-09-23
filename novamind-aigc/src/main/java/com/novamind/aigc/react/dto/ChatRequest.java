package com.novamind.aigc.react.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Chat request DTO
 */
@Data
@Schema(description = "AI Chat Request")
public class ChatRequest {
    
    @Schema(description = "Session ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
    
    @Schema(description = "User question", example = "推荐一些Java课程")
    private String question;
    
    @Schema(description = "Maximum iterations", example = "10")
    private Integer maxIterations = 10;
}
