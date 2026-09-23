package com.novamind.common.autoconfigure.datasource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.MDC;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <h1>R15 慢查询拦截器（MyBatis Interceptor）</h1>
 *
 * <p>统计 SQL 执行耗时，超过阈值（默认 {@link #DEFAULT_THRESHOLD_MS}=200ms）</p>
 * <ol>
 *   <li>走 Micrometer {@link Timer}（histogram=true，可打 P50/P95/P99），按 {@code type}（select/update/...）
 *       和 {@code msId}（Mapper 方法完整 ID）打 tag；</li>
 *   <li>打 SLF4J {@code WARN}，带 traceId / SQL / 参数 / 耗时，便于在 Kibana / Loki 中检索。</li>
 * </ol>
 *
 * <h3>使用</h3>
 * <p>由 {@code HikariAutoConfiguration} 同目录的 {@code MysqlSlowQueryAutoConfiguration}
 * 通过 {@code ConfigurationCustomizer} 注册到 {@code MybatisConfiguration}。</p>
 *
 * <h3>为什么不复用 {@code SlowSqlLogger}？</h3>
 * <ul>
 *   <li>{@code SlowSqlLogger} 阈值 500ms，且只走日志；</li>
 *   <li>{@code SlowQueryInterceptor} 阈值 200ms，日志 + Micrometer 双通道输出。</li>
 * </ul>
 *
 * <h3>面试要点</h3>
 * <ul>
 *   <li>Micrometer 的 {@code Timer.builder().publishPercentileHistogram()} 会自动注册
 *       {@code SLO} 桶（默认 1ms~30s 几十个桶），可直接用于 Prometheus histogram_quantile；</li>
 *   <li>histogram 比简单 {@code time(...)} 多带 {@code _bucket / _count / _sum} 三个指标，但能
 *       在任意分位数聚合；</li>
 *   <li>高基数 tag（如 {@code msId}）要小心，否则 Prometheus 内存爆炸。本类按需懒注册，
 *       同一 {@code msId} 第一次执行后才创建，避免冷门 SQL 把 tag 撑爆。</li>
 * </ul>
 *
 * @author R15-refactor
 */
@Data
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        // MyBatis 3.5.x 的 Executor 不存在 (MappedStatement, Object, RowBounds, CacheKey, BoundSql)
        // 这个重载——必须带上 ResultHandler，否则 Plugin.getSignatureMap() 抛
        // NoSuchMethodException，任何一次 SQL 都会以 "Error opening session" 失败。
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "queryCursor",
                args = {MappedStatement.class, Object.class, RowBounds.class})
})
public class SlowQueryInterceptor implements Interceptor {

    /** 慢查询阈值默认 200ms（R15 改造目标） */
    public static final long DEFAULT_THRESHOLD_MS = 200L;

    /** Micrometer metric name */
    public static final String METRIC_NAME = "mybatis.slowquery";

    /** 慢查询阈值（ms），可由 @ConfigurationProperties 覆盖 */
    private long thresholdMs = DEFAULT_THRESHOLD_MS;

    /** 是否打 WARN 日志 */
    private boolean warnLogEnabled = true;

    /** 是否打印完整 SQL + 参数 */
    private boolean printFullSql = true;

    /** 单条 SQL 参数序列化最大长度 */
    private int maxParamLength = 500;

    /** MDC 中 traceId 的 key */
    private String traceIdKey = "traceId";

    /** Micrometer MeterRegistry（可空，空则只打日志） */
    private volatile MeterRegistry meterRegistry;

    /** 已注册的 Timer 缓存：key = "{type}:{msId}"，避免重复注册 */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 由 Spring 启动期注入 Micrometer（构造函数注入 MeterRegistry 可选）。
     */
    public SlowQueryInterceptor() {
    }

    public SlowQueryInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNs = System.nanoTime();
        Throwable error = null;
        Object result;
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long elapsedNs = System.nanoTime() - startNs;
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs);
            try {
                recordMetric(invocation, elapsedNs);
                if (elapsedMs >= thresholdMs || error != null) {
                    if (warnLogEnabled) {
                        formatWarnLog(invocation, elapsedMs, error);
                    }
                }
            } catch (Exception ignore) {
                // 监控/日志失败不影响业务
            }
        }
    }

    /**
     * 上报到 Micrometer：histogram=true（自动注册 SLO 桶，可打 P95/P99）。
     */
    private void recordMetric(Invocation invocation, long elapsedNs) {
        if (meterRegistry == null) {
            return;
        }
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String type = ms.getSqlCommandType().name();
        String msId = ms.getId();
        String key = type + ":" + msId;
        Timer timer = timerCache.computeIfAbsent(key, k -> Timer.builder(METRIC_NAME)
                .description("MyBatis SQL execution latency")
                .tags(Tags.of("type", type, "msId", msId))
                // 关键：histogram=true，Prometheus 端才能用 histogram_quantile
                .publishPercentileHistogram()
                .register(meterRegistry));
        timer.record(elapsedNs, TimeUnit.NANOSECONDS);
    }

    private void formatWarnLog(Invocation invocation, long costMs, Throwable error) {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();
        SqlCommandType type = ms.getSqlCommandType();
        String traceId = MDC.get(traceIdKey);
        StringBuilder sb = new StringBuilder(512);
        sb.append("[SlowQuery] cost=").append(costMs).append("ms")
                .append(", type=").append(type)
                .append(", msId=").append(ms.getId());
        if (traceId != null) {
            sb.append(", traceId=").append(traceId);
        }
        if (error != null) {
            sb.append(", error=").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage());
        }
        if (printFullSql) {
            sb.append("\n  SQL  : ").append(sql);
            sb.append("\n  Param: ").append(truncateParam(boundSql));
        }
        log.warn(sb.toString());
    }

    private String truncateParam(BoundSql boundSql) {
        Object param = boundSql.getParameterObject();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        StringBuilder sb = new StringBuilder(64);
        sb.append("{");
        if (param != null) {
            String s = String.valueOf(param);
            if (s.length() > maxParamLength) {
                sb.append("obj=").append(s, 0, maxParamLength).append("...(truncated)");
            } else {
                sb.append("obj=").append(s);
            }
        }
        if (mappings != null && !mappings.isEmpty()) {
            sb.append(", mappings=[");
            for (int i = 0; i < mappings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(mappings.get(i).getProperty());
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String ms = properties.getProperty("thresholdMs");
        if (ms != null) {
            try {
                this.thresholdMs = Long.parseLong(ms);
            } catch (Exception ignore) {
            }
        }
        String full = properties.getProperty("printFullSql");
        if (full != null) {
            this.printFullSql = Boolean.parseBoolean(full);
        }
        String max = properties.getProperty("maxParamLength");
        if (max != null) {
            try {
                this.maxParamLength = Integer.parseInt(max);
            } catch (Exception ignore) {
            }
        }
        String traceKey = properties.getProperty("traceIdKey");
        if (traceKey != null) {
            this.traceIdKey = traceKey;
        }
        String warn = properties.getProperty("warnLogEnabled");
        if (warn != null) {
            this.warnLogEnabled = Boolean.parseBoolean(warn);
        }
    }
}