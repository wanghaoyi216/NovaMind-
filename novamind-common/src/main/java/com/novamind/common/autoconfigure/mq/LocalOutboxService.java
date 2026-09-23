package com.novamind.common.autoconfigure.mq;

import cn.hutool.core.lang.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static com.novamind.common.constants.Constant.REQUEST_ID_HEADER;

/**
 * <h1>本地消息表 (P0 改造：Outbox 模式)</h1>
 *
 * <p>解决的问题：RabbitMQ 发送消息如果 broker / 网络异常，会出现「业务已提交但消息丢失」
 * 或者「消息已发出但本地事务回滚」的场景。本服务对外提供 {@link #send} 接口，
 * 发送时先把消息持久化到本地 outbox 表（这里是内存版，生产建议替换为 MySQL 表），
 * 再调用 {@link RabbitTemplate} 投递。当 {@link RabbitTemplate.ConfirmCallback}
 * 收到 nack 或 {@link RabbitTemplate.ReturnCallback} 收到 return 时，把状态标记为
 * {@code FAILED}，由后台 {@link #retryPending()} 定时扫描重投。</p>
 *
 * <p><b>CAP 语义</b>：相比把 outbox 写 DB，用内存 Map 会有服务重启丢消息的风险，
 * 所以这是一个开发态实现；上线前请把 {@code OUTBOX} 替换成一张真实的 MySQL 表。</p>
 *
 * @author P0-refactor
 */
@Slf4j
@Service
@EnableConfigurationProperties(RabbitMqProperties.class)
@EnableScheduling
public class LocalOutboxService implements SmartInitializingSingleton {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitMqProperties properties;
    private final ObjectProvider<LocalOutboxStore> storeProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /**
     * outbox 内存存储；如未注入自定义 store，则用 ConcurrentHashMap 实现。
     * P0 bugfix（R3）：原实现在 {@code afterSingletonsInstantiated} 才赋值，
     * 若有代码在容器刷新完成前调用 send() 会 NPE。现在改为构造期立即解析
     * （{@code getIfAvailable(default)} 只是"此刻查找、找不到用默认值"，
     * 不强制依赖其他 bean 完成初始化，因此在构造器中调用是安全的），
     * 同时保留 {@link SmartInitializingSingleton} 回调仅用于挂载 confirm/return 监听
     * ——那一步确实需要等所有单例就绪后才能拿到业务方定制的 RabbitTemplate。
     */
    private final LocalOutboxStore store;

    /**
     * 是否已挂上 confirm / return 回调，避免重复设置
     */
    private volatile boolean callbackAttached = false;

    /**
     * 手动构造器：替代 {@code @RequiredArgsConstructor}，以便在构造期就解析 outbox store。
     */
    public LocalOutboxService(RabbitTemplate rabbitTemplate,
                              ObjectMapper objectMapper,
                              RabbitMqProperties properties,
                              ObjectProvider<LocalOutboxStore> storeProvider,
                              ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.storeProvider = storeProvider;
        this.meterRegistryProvider = meterRegistryProvider;
        this.store = storeProvider.getIfAvailable(InMemoryOutboxStore::new);
    }

    /**
     * Spring 初始化完所有单例后挂回调，保证业务方自定义的 RabbitTemplate 已就绪。
     * （store 已在构造期解析完成，这里只负责挂 confirm/return 监听。）
     */
    @Override
    public void afterSingletonsInstantiated() {
        attachCallbacks();
        log.info("[LocalOutbox] 启动完成, outboxEnabled={}, store={}", properties.isOutboxEnabled(),
                store.getClass().getSimpleName());
    }

    /**
     * 计算指数退避重试间隔（毫秒），附加随机抖动 Jitter
     */
    public long computeBackoffMs(int retryCount) {
        return Math.min(60_000L, 1000L * (1L << retryCount)) + ThreadLocalRandom.current().nextLong(0, 500);
    }

    /**
     * 业务方调用此方法发送消息。流程：
     * <ol>
     *   <li>把消息体 / exchange / routingKey 持久化到 outbox（status=PENDING）</li>
     *   <li>调用 {@link RabbitTemplate#convertAndSend} 投递（带 CorrelationData）</li>
     *   <li>ConfirmCallback 收到 ack → status=SENT</li>
     *   <li>ConfirmCallback 收到 nack 或 ReturnCallback 收到 → status=FAILED</li>
     * </ol>
     */
    public <T> void send(String exchange, String routingKey, T payload) {
        if (!properties.isOutboxEnabled()) {
            // outbox 关闭 → 直投
            CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, cd);
            return;
        }
        // 1.先落 outbox
        OutboxRecord record = new OutboxRecord();
        record.setExchange(exchange);
        record.setRoutingKey(routingKey);
        record.setPayload(serialize(payload));
        record.setStatus(OutboxStatus.PENDING);
        record.setCreateTime(LocalDateTime.now());
        record.setRetryCount(0);
        store.save(record);
        if (record.getId() == null) {
            record.setId(UUID.randomUUID().toString(true));
        }

        // 2.再投递，CorrelationData 关联 outbox id
        CorrelationData cd = new CorrelationData(record.getId());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, cd);
        } catch (Exception ex) {
            // 投递瞬间失败：标记 FAILED，等定时任务重投
            log.error("[LocalOutbox] 发送消息异常, id={}, exchange={}, routingKey={}",
                    record.getId(), exchange, routingKey, ex);
            record.setStatus(OutboxStatus.FAILED);
            record.setLastError(ex.getMessage());
            record.setRetryCount(record.getRetryCount() + 1);
            long backoffMs = computeBackoffMs(record.getRetryCount());
            record.setNextRetryTime(LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
            record.setUpdateTime(LocalDateTime.now());
            store.update(record);
        }
    }

    /**
     * 挂 confirm + return 回调
     */
    private void attachCallbacks() {
        if (callbackAttached) {
            return;
        }
        synchronized (this) {
            if (callbackAttached) {
                return;
            }
            rabbitTemplate.setMandatory(true);
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                if (correlationData == null || correlationData.getId() == null) {
                    // 没有 outbox 关联的消息，忽略
                    return;
                }
                String outboxId = correlationData.getId();
                OutboxRecord r = store.findById(outboxId);
                if (r == null) {
                    return;
                }
                if (ack) {
                    r.setStatus(OutboxStatus.SENT);
                    r.setLastError(null);
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                    log.debug("[LocalOutbox] 消息确认成功, id={}", outboxId);
                } else {
                    r.setStatus(OutboxStatus.FAILED);
                    r.setLastError(cause);
                    r.setRetryCount(r.getRetryCount() + 1);
                    long backoffMs = computeBackoffMs(r.getRetryCount());
                    r.setNextRetryTime(LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                    log.warn("[LocalOutbox] 消息确认失败, id={}, cause={}", outboxId, cause);
                }
            });
            rabbitTemplate.setReturnsCallback(returned -> {
                String outboxId = returned.getMessage().getMessageProperties().getCorrelationId();
                if (outboxId == null) {
                    return;
                }
                OutboxRecord r = store.findById(outboxId);
                if (r != null) {
                    r.setStatus(OutboxStatus.FAILED);
                    r.setLastError("returned: no route, replyText=" + returned.getReplyText());
                    r.setRetryCount(r.getRetryCount() + 1);
                    long backoffMs = computeBackoffMs(r.getRetryCount());
                    r.setNextRetryTime(LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                    log.warn("[LocalOutbox] 消息无法路由, id={}, exchange={}, routingKey={}, reply={}",
                            outboxId, returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
                }
            });
            callbackAttached = true;
            log.info("[LocalOutbox] confirm + return callback 已挂载到 RabbitTemplate");
        }
    }

    /**
     * 定时任务：扫描 PENDING / FAILED 的消息，重新投递
     */
    @Scheduled(fixedDelayString = "${novamind.rabbitmq.outbox-retry-interval-ms:30000}")
    public void retryPending() {
        if (!properties.isOutboxEnabled() || store == null) {
            return;
        }
        try {
            // 透传 trace id
            String trace = MDC.get(REQUEST_ID_HEADER);
            if (trace != null) {
                MDC.put(REQUEST_ID_HEADER, trace);
            }

            int size = properties.getOutboxBatchSize();
            int maxRetries = properties.getOutboxMaxRetries();
            List<OutboxRecord> pending = store.findPending(size, maxRetries + 1);
            if (pending.isEmpty()) {
                return;
            }
            log.info("[LocalOutbox] 开始重投, count={}", pending.size());
            for (OutboxRecord r : pending) {
                if (r.getRetryCount() >= maxRetries) {
                    r.setStatus(OutboxStatus.DEAD);
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                    meterRegistryProvider.ifAvailable(registry ->
                            Counter.builder("novamind.outbox.dead")
                                    .description("messages that exceeded max retries")
                                    .register(registry)
                                    .increment()
                    );
                    log.error("[LocalOutbox] 达到最大重试次数，转人工, id={}", r.getId());
                    continue;
                }
                try {
                    Object payload = deserialize(r.getPayload(), Object.class);
                    CorrelationData cd = new CorrelationData(r.getId());
                    rabbitTemplate.convertAndSend(r.getExchange(), r.getRoutingKey(), payload, cd);
                    r.setRetryCount(r.getRetryCount() + 1);
                    long backoffMs = computeBackoffMs(r.getRetryCount());
                    r.setNextRetryTime(LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
                    r.setStatus(OutboxStatus.PENDING); // 重新置回 PENDING 等回调确认
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                } catch (Exception ex) {
                    r.setRetryCount(r.getRetryCount() + 1);
                    long backoffMs = computeBackoffMs(r.getRetryCount());
                    r.setNextRetryTime(LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
                    r.setStatus(OutboxStatus.FAILED);
                    r.setLastError(ex.getMessage());
                    r.setUpdateTime(LocalDateTime.now());
                    store.update(r);
                    log.warn("[LocalOutbox] 重投失败, id={}", r.getId(), ex);
                }
            }
        } catch (Exception e) {
            log.error("[LocalOutbox] retryPending 出错", e);
        }
    }

    /* ---------- 序列化辅助 ---------- */
    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[LocalOutbox] 序列化失败, 降级 toString", e);
            return String.valueOf(obj);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            // 兜底：原始 payload 不是 JSON 字符串，直接以 string 形式发出
            return type.cast(json);
        }
    }

    /* ---------- 内部数据结构 ---------- */
    public enum OutboxStatus {
        PENDING, SENT, FAILED, DEAD
    }

    @Data
    public static class OutboxRecord {
        private String id;
        private String exchange;
        private String routingKey;
        private String payload;
        private OutboxStatus status;
        private int retryCount;
        private LocalDateTime nextRetryTime;
        private String lastError;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 抽象 outbox 存储。生产环境请实现一个基于 MySQL 表的实现。
     */
    public interface LocalOutboxStore {
        void save(OutboxRecord record);

        void update(OutboxRecord record);

        OutboxRecord findById(String id);

        /**
         * 查 PENDING/FAILED 的待重投记录，过滤超 maxRetries 的
         */
        List<OutboxRecord> findPending(int limit, int maxRetries);
    }

    /**
     * 默认内存版 store，仅供本地开发 / 单测使用
     */
    public static class InMemoryOutboxStore implements LocalOutboxStore {
        private final ConcurrentHashMap<String, OutboxRecord> map = new ConcurrentHashMap<>();

        @Override
        public void save(OutboxRecord record) {
            if (record.getId() == null) {
                record.setId(UUID.randomUUID().toString(true));
            }
            map.put(record.getId(), record);
        }

        @Override
        public void update(OutboxRecord record) {
            record.setUpdateTime(LocalDateTime.now());
            map.put(record.getId(), record);
        }

        @Override
        public OutboxRecord findById(String id) {
            return map.get(id);
        }

        @Override
        public List<OutboxRecord> findPending(int limit, int maxRetries) {
            List<OutboxRecord> res = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (OutboxRecord r : map.values()) {
                if ((r.getStatus() == OutboxStatus.PENDING || r.getStatus() == OutboxStatus.FAILED)
                        && r.getRetryCount() < maxRetries
                        && (r.getNextRetryTime() == null || !r.getNextRetryTime().isAfter(now))) {
                    res.add(r);
                    if (res.size() >= limit) {
                        break;
                    }
                }
            }
            return new CopyOnWriteArrayList<>(res);
        }
    }
}
