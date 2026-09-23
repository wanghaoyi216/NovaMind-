package com.novamind.common.autoconfigure.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcOutboxStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        store = new JdbcOutboxStore(jdbcTemplate);
    }

    @Test
    @DisplayName("测试 JdbcOutboxStore.save 设置自增主键")
    void testSave() {
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("id", 1001L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        LocalOutboxService.OutboxRecord record = new LocalOutboxService.OutboxRecord();
        record.setExchange("ex.test");
        record.setRoutingKey("rk.test");
        record.setPayload("{}");
        record.setStatus(LocalOutboxService.OutboxStatus.PENDING);
        record.setRetryCount(0);

        store.save(record);

        assertEquals("1001", record.getId());
        verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    @DisplayName("测试 JdbcOutboxStore.update")
    void testUpdate() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        LocalOutboxService.OutboxRecord record = new LocalOutboxService.OutboxRecord();
        record.setId("1001");
        record.setStatus(LocalOutboxService.OutboxStatus.FAILED);
        record.setRetryCount(2);
        record.setNextRetryTime(LocalDateTime.now().plusSeconds(4));

        store.update(record);

        verify(jdbcTemplate, times(1)).update(
                contains("UPDATE mq_outbox"),
                eq("FAILED"),
                eq(2),
                any(LocalDateTime.class),
                isNull(),
                any(LocalDateTime.class),
                eq(1001L)
        );
    }

    @Test
    @DisplayName("测试 JdbcOutboxStore.findPending")
    void testFindPending() {
        LocalOutboxService.OutboxRecord r = new LocalOutboxService.OutboxRecord();
        r.setId("1001");
        r.setStatus(LocalOutboxService.OutboxStatus.PENDING);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(r));

        List<LocalOutboxService.OutboxRecord> result = store.findPending(10, 3);

        assertEquals(1, result.size());
        assertEquals("1001", result.get(0).getId());
        verify(jdbcTemplate, times(1)).query(
                contains("WHERE status IN ('PENDING', 'FAILED')"),
                any(RowMapper.class),
                eq(3),
                any(LocalDateTime.class),
                eq(10)
        );
    }
}
