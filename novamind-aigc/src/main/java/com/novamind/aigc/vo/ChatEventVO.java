package com.novamind.aigc.vo;

import com.novamind.aigc.enums.ChatEventTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVO {

    /**
     * 文本内容
     */
    private Object eventData;

    /**
     * 事件类型，1001-数据事件，1002-停止事件，1003-参数事件，1101-思考事件，1102-行动事件，1103-观察事件，1104-指标事件
     */
    private int eventType;

    /**
     * 事件名称，便于前端直接映射 ReAct 分段
     */
    private String eventName;

    /**
     * 当前事件所属阶段，例如 PREPARE、STREAM、TOOL、FINAL
     */
    private String phase;

    /**
     * 请求标识，贯穿 SSE 链路
     */
    private String requestId;

    /**
     * 事件时间戳
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    /**
     * 额外元数据，承载性能指标、工具结果、图谱上下文等轻量信息
     */
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public static ChatEventVO of(ChatEventTypeEnum eventType, Object eventData, String requestId, String phase) {
        return ChatEventVO.builder()
                .eventType(eventType.getValue())
                .eventName(eventType.name())
                .eventData(eventData)
                .requestId(requestId)
                .phase(phase)
                .build();
    }

    public static ChatEventVO of(ChatEventTypeEnum eventType, Object eventData, String requestId, String phase, Map<String, Object> metadata) {
        return ChatEventVO.builder()
                .eventType(eventType.getValue())
                .eventName(eventType.name())
                .eventData(eventData)
                .requestId(requestId)
                .phase(phase)
                .metadata(metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata))
                .build();
    }

}
