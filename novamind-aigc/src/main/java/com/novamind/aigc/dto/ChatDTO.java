package com.novamind.aigc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDTO {

    /**
     * 用户的问题
     */
    private String question;
    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 多模态输入，按前端上传/引用顺序传入
     */
    @Builder.Default
    private List<ChatMediaDTO> media = new ArrayList<>();

    /**
     * 前端补充的客户端元数据，例如页面来源、主题、设备信息
     */
    @Builder.Default
    private Map<String, Object> clientMetadata = Map.of();
}
