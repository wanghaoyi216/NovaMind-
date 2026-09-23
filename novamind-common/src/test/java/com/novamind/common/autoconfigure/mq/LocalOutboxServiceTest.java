package com.novamind.common.autoconfigure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalOutboxServiceTest {

    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private RabbitMqProperties properties;
    private LocalOutboxService.InMemoryOutboxStore store;
    private MeterRegistry meterRegistry;
    private LocalOutboxService service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();
        properties = new RabbitMqProperties();
        properties.setOutboxEnabled(true);
        properties.setOutboxMaxRetries(3);
        properties.setOutboxBatchSize(10);

        store = new LocalOutboxService.InMemoryOutboxStore();
        meterRegistry = new SimpleMeterRegistry();

        @SuppressWarnings("unchecked")
        ObjectProvider<LocalOutboxService.LocalOutboxStore> storeProvider = mock(ObjectProvider.class);
        when(storeProvider.getIfAvailable(any())).thenReturn(store);

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<MeterRegistry> consumer = invocation.getArgument(0);
            consumer.accept(meterRegistry);
            return null;
        }).when(meterRegistryProvider).ifAvailable(any());

        service = new LocalOutboxService(rabbitTemplate, objectMapper, properties, storeProvider, meterRegistryProvider);
    }

    @Test
    @DisplayName("测试指数退避时间计算与 Jitter 范围")
    void testComputeBackoffMs() {
        for (int i = 0; i < 50; i++) {
            // retryCount = 0 => 1000 * 1 = 1000ms + [0, 500)
            long backoff0 = service.computeBackoffMs(0);
            assertTrue(backoff0 >= 1000 && backoff0 < 1500, "backoff0 should be in [1000, 1500), got: " + backoff0);

            // retryCount = 1 => 1000 * 2 = 2000ms + [0, 500)
            long backoff1 = service.computeBackoffMs(1);
            assertTrue(backoff1 >= 2000 && backoff1 < 2500, "backoff1 should be in [2000, 2500), got: " + backoff1);

            // retryCount = 2 => 1000 * 4 = 4000ms + [0, 500)
            long backoff2 = service.computeBackoffMs(2);
            assertTrue(backoff2 >= 4000 && backoff2 < 4500, "backoff2 should be in [4000, 4500), got: " + backoff2);

            // retryCount = 3 => 1000 * 8 = 8000ms + [0, 500)
            long backoff3 = service.computeBackoffMs(3);
            assertTrue(backoff3 >= 8000 && backoff3 < 8500, "backoff3 should be in [8000, 8500), got: " + backoff3);

            // retryCount = 10 => min(60000, 1024000) = 60000ms + [0, 500)
            long backoff10 = service.computeBackoffMs(10);
            assertTrue(backoff10 >= 60000 && backoff10 < 60500, "backoff10 should be capped at [60000, 60500), got: " + backoff10);
        }
    }

    @Test
    @DisplayName("测试待重试消息正常重投并更新 nextRetryTime 与 retryCount")
    void testRetryPendingSuccess() {
        LocalOutboxService.OutboxRecord record = new LocalOutboxService.OutboxRecord();
        record.setId("test-outbox-1");
        record.setExchange("test.exchange");
        record.setRoutingKey("test.key");
        record.setPayload("{\"msg\":\"hello\"}");
        record.setStatus(LocalOutboxService.OutboxStatus.FAILED);
        record.setRetryCount(1);
        record.setCreateTime(LocalDateTime.now().minusMinutes(5));
        store.save(record);

        service.retryPending();

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("test.exchange"),
                eq("test.key"),
                any(Object.class),
                any(CorrelationData.class)
        );

        LocalOutboxService.OutboxRecord updated = store.findById("test-outbox-1");
        assertNotNull(updated);
        assertEquals(2, updated.getRetryCount());
        assertEquals(LocalOutboxService.OutboxStatus.PENDING, updated.getStatus());
        assertNotNull(updated.getNextRetryTime());
        assertTrue(updated.getNextRetryTime().isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    @DisplayName("测试超过最大重试次数转 DEAD 状态并触发 Micrometer 计数器")
    void testRetryPendingDeadTransitionAndCounter() {
        LocalOutboxService.OutboxRecord record = new LocalOutboxService.OutboxRecord();
        record.setId("test-outbox-dead");
        record.setExchange("test.exchange");
        record.setRoutingKey("test.key");
        record.setPayload("{\"msg\":\"dead-payload\"}");
        record.setStatus(LocalOutboxService.OutboxStatus.FAILED);
        record.setRetryCount(3); // properties.outboxMaxRetries = 3
        record.setCreateTime(LocalDateTime.now().minusMinutes(10));
        store.save(record);

        service.retryPending();

        // 不应再向 RabbitMQ 发送消息
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        // 状态转为 DEAD
        LocalOutboxService.OutboxRecord updated = store.findById("test-outbox-dead");
        assertNotNull(updated);
        assertEquals(LocalOutboxService.OutboxStatus.DEAD, updated.getStatus());

        // 校验 Micrometer Counter
        Counter deadCounter = meterRegistry.find("novamind.outbox.dead").counter();
        assertNotNull(deadCounter, "Micrometer counter novamind.outbox.dead should exist");
        assertEquals(1.0, deadCounter.count(), 0.001);
    }
}
