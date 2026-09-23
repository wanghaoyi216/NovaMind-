package com.novamind.common.autoconfigure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <h1>基于 MySQL 的本地消息表存储实现 (Outbox 模式)</h1>
 *
 * <p>使用 Spring {@link JdbcTemplate} 进行持久化操作，
 * 插入时通过 {@link GeneratedKeyHolder} 获取数据库自增主键。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcOutboxStore implements LocalOutboxService.LocalOutboxStore {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LocalOutboxService.OutboxRecord> ROW_MAPPER = (rs, rowNum) -> {
        LocalOutboxService.OutboxRecord record = new LocalOutboxService.OutboxRecord();
        record.setId(rs.getString("id"));
        record.setExchange(rs.getString("exchange"));
        record.setRoutingKey(rs.getString("routing_key"));
        record.setPayload(rs.getString("payload"));
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            record.setStatus(LocalOutboxService.OutboxStatus.valueOf(statusStr));
        }
        record.setRetryCount(rs.getInt("retry_count"));
        Timestamp nextRetryTime = rs.getTimestamp("next_retry_time");
        if (nextRetryTime != null) {
            record.setNextRetryTime(nextRetryTime.toLocalDateTime());
        }
        record.setLastError(rs.getString("last_error"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) {
            record.setCreateTime(createTime.toLocalDateTime());
        }
        Timestamp updateTime = rs.getTimestamp("update_time");
        if (updateTime != null) {
            record.setUpdateTime(updateTime.toLocalDateTime());
        }
        return record;
    };

    @Override
    public void save(LocalOutboxService.OutboxRecord record) {
        String sql = "INSERT INTO mq_outbox (exchange, routing_key, payload, status, retry_count, next_retry_time, last_error, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getExchange());
            ps.setString(2, record.getRoutingKey());
            ps.setString(3, record.getPayload());
            ps.setString(4, record.getStatus() != null ? record.getStatus().name() : LocalOutboxService.OutboxStatus.PENDING.name());
            ps.setInt(5, record.getRetryCount());
            ps.setObject(6, record.getNextRetryTime());
            ps.setString(7, record.getLastError());
            ps.setObject(8, record.getCreateTime() != null ? record.getCreateTime() : LocalDateTime.now());
            ps.setObject(9, record.getUpdateTime() != null ? record.getUpdateTime() : LocalDateTime.now());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            record.setId(String.valueOf(keyHolder.getKey().longValue()));
        }
    }

    @Override
    public void update(LocalOutboxService.OutboxRecord record) {
        String sql = "UPDATE mq_outbox SET status = ?, retry_count = ?, next_retry_time = ?, last_error = ?, update_time = ? WHERE id = ?";
        LocalDateTime updateTime = record.getUpdateTime() != null ? record.getUpdateTime() : LocalDateTime.now();
        record.setUpdateTime(updateTime);

        Object idParam;
        try {
            idParam = Long.parseLong(record.getId());
        } catch (NumberFormatException e) {
            idParam = record.getId();
        }

        jdbcTemplate.update(sql,
                record.getStatus() != null ? record.getStatus().name() : null,
                record.getRetryCount(),
                record.getNextRetryTime(),
                record.getLastError(),
                updateTime,
                idParam);
    }

    @Override
    public LocalOutboxService.OutboxRecord findById(String id) {
        String sql = "SELECT id, exchange, routing_key, payload, status, retry_count, next_retry_time, last_error, create_time, update_time FROM mq_outbox WHERE id = ?";
        Object idParam;
        try {
            idParam = Long.parseLong(id);
        } catch (NumberFormatException e) {
            idParam = id;
        }

        List<LocalOutboxService.OutboxRecord> list = jdbcTemplate.query(sql, ROW_MAPPER, idParam);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<LocalOutboxService.OutboxRecord> findPending(int limit, int maxRetries) {
        String sql = "SELECT id, exchange, routing_key, payload, status, retry_count, next_retry_time, last_error, create_time, update_time " +
                "FROM mq_outbox " +
                "WHERE status IN ('PENDING', 'FAILED') " +
                "  AND retry_count < ? " +
                "  AND (next_retry_time IS NULL OR next_retry_time <= ?) " +
                "ORDER BY create_time ASC " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, maxRetries, LocalDateTime.now(), limit);
    }
}
