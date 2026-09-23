package com.novamind.aigc.vo;

public record ExportSessionVO(
            Long userId,
            String sessionId,
            String content
    ){};