package com.novamind.aigc.service.support;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.ToolResultHolder;
import com.novamind.aigc.constants.Constant;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.enums.ChatGenerationState;
import com.novamind.aigc.service.AiStreamTelemetry;
import com.novamind.aigc.vo.ChatEventVO;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 统一的流式事件处理管道，负责：
 * 1. Redis 大模型生成状态（状态机）维护
 * 2. 多媒体与普通内容流式分片推送
 * 3. Telemetry 性能指标收集与周期推送
 * 4. 下游关闭 SSE 连接时的生命周期监控与会话历史挽救
 * 5. 模型输出完毕后触发工具调用检查、结果清洗与事件映射
 */
@Slf4j
@Builder
public class ChatStreamPipeline {

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatMemory chatMemory;
    private final String sessionId;
    private final String requestId;
    private final String conversationId;

    // 流式性能收集器
    private final AiStreamTelemetry.Tracker telemetryTracker;

    // 流式输出完全结束后的回调（入参为模型生成的完整文本内容）
    private final Consumer<String> onCompleteHook;
    // 整个流式事件处理管道生命周期最后一刻执行的回调
    private final Runnable postStreamHook;

    // 是否输出 Telemetry 指标数据
    private final boolean includeMetrics;
    // 自定义尾部停止事件，不提供则使用默认
    private final ChatEventVO stopEvent;

    public Flux<ChatEventVO> execute(Flux<ChatResponse> chatResponseFlux) {
        var outputBuilder = new StringBuilder();
        var generateStatusOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        var isDone = new AtomicBoolean(false);

        // 核心文本生成及分片转化流
        var mainStream = chatResponseFlux
                .doFirst(() -> {
                    log.debug("Model response stream started. sessionId={}", sessionId);
                    generateStatusOps.put(sessionId, ChatGenerationState.GENERATING.getCode());
                })
                .doOnError(throwable -> {
                    log.error("Model response stream error. sessionId={}", sessionId, throwable);
                    if (isDone.compareAndSet(false, true)) {
                        generateStatusOps.delete(sessionId);
                    }
                })
                .doOnComplete(() -> {
                    log.debug("Model response stream completed. sessionId={}", sessionId);
                    if (isDone.compareAndSet(false, true)) {
                        generateStatusOps.delete(sessionId);
                    }
                })
                .doOnCancel(() -> {
                    log.debug("Model response stream cancelled by client. sessionId={}", sessionId);
                    if (isDone.compareAndSet(false, true)) {
                        generateStatusOps.delete(sessionId);
                    }
                    // 挽救已经生成的回复片段，保存至 ChatMemory 历史记忆中
                    var content = outputBuilder.toString();
                    if (StrUtil.isNotEmpty(content)) {
                        this.chatMemory.add(conversationId, new AssistantMessage(content));
                    }
                })
                .takeWhile(response -> generateStatusOps.get(sessionId) != null)
                .map(chatResponse -> {
                    var text = chatResponse.getResult().getOutput().getText();
                    outputBuilder.append(text);

                    // 喂给性能监视器
                    if (telemetryTracker != null) {
                        telemetryTracker.onChunk(chatResponse, text);
                    }

                    // 检查是否结束，如果是，登记消息 ID 便于后面工具提取
                    var finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if (StrUtil.equals(finishReason, Constant.STOP)) {
                        var messageId = chatResponse.getMetadata().getId();
                        ToolResultHolder.put(messageId, Constant.REQUEST_ID, requestId);
                    }

                    return ChatEventVO.of(ChatEventTypeEnum.DATA, text, requestId, "STREAM");
                });

        // 尾声阶段延迟计算事件流
        var tailEvents = Flux.defer(() -> {
            var finalMetrics = telemetryTracker != null ? telemetryTracker.snapshot() : Map.<String, Object>of();
            var finalAnswer = outputBuilder.toString();

            // 触发模型接收完毕逻辑
            if (onCompleteHook != null) {
                onCompleteHook.accept(finalAnswer);
            }
            if (postStreamHook != null) {
                postStreamHook.run();
            }

            var defaultStopEvent = stopEvent != null ? stopEvent : ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.STOP.getValue())
                    .eventName(ChatEventTypeEnum.STOP.name())
                    .phase("FINAL")
                    .build();

            var result = ToolResultHolder.get(requestId);
            if (ObjectUtil.isNotEmpty(result)) {
                // 清理 ToolResultHolder 缓存
                ToolResultHolder.remove(requestId);
                if (includeMetrics) {
                    return Flux.just(
                            ChatEventVO.of(ChatEventTypeEnum.ACTION, "工具调用已完成", requestId, "TOOL", result),
                            ChatEventVO.of(ChatEventTypeEnum.OBSERVATION, result, requestId, "TOOL_RESULT"),
                            ChatEventVO.of(ChatEventTypeEnum.PARAM, result, requestId, "COMPAT"),
                            ChatEventVO.of(ChatEventTypeEnum.METRICS, finalMetrics, requestId, "METRICS"),
                            defaultStopEvent
                    );
                } else {
                    return Flux.just(
                            ChatEventVO.of(ChatEventTypeEnum.ACTION, "工具调用已完成", requestId, "TOOL", result),
                            ChatEventVO.of(ChatEventTypeEnum.OBSERVATION, result, requestId, "TOOL_RESULT"),
                            ChatEventVO.of(ChatEventTypeEnum.PARAM, result, requestId, "COMPAT"),
                            defaultStopEvent
                    );
                }
            }

            if (includeMetrics) {
                return Flux.just(
                        ChatEventVO.of(ChatEventTypeEnum.OBSERVATION, "本轮未触发工具调用，直接完成回答", requestId, "FINAL"),
                        ChatEventVO.of(ChatEventTypeEnum.METRICS, finalMetrics, requestId, "METRICS"),
                        defaultStopEvent
                );
            } else {
                return Flux.just(defaultStopEvent);
            }
        });

        return Flux.concat(mainStream, tailEvents)
                .onErrorResume(throwable -> {
                    log.error("Pipeline runtime error for sessionId={}, requestId={}", sessionId, requestId, throwable);
                    var defaultStopEvent = stopEvent != null ? stopEvent : ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.STOP.getValue())
                            .eventName(ChatEventTypeEnum.STOP.name())
                            .phase("FINAL")
                            .build();
                    return Flux.just(
                            ChatEventVO.of(ChatEventTypeEnum.ERROR, throwable.getMessage(), requestId, "ERROR"),
                            defaultStopEvent
                    );
                });
    }
}
