package com.novamind.common.autoconfigure.mybatis;

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

/**
 * <h1>慢 SQL 监控 (P0 改造)</h1>
 *
 * <p>基于 MyBatis 拦截器（{@link Interceptor}），拦截 {@link Executor} 的
 * {@code update / query / queryCursor}，掐时间计算耗时。
 * 阈值默认 500ms，超过即 {@code WARN} 级别打印完整 SQL + 参数 + 耗时，
 * 并附带 MDC 中的 traceId，方便和链路日志打通。</p>
 *
 * <p>为什么不直接用 {@code datasource-proxy}?</p>
 * <ol>
 *   <li>项目用 MyBatis-Plus，原生 Interceptor 注册最简单；</li>
 *   <li>走 Executor 层能拿到 {@link MappedStatement} 的 ID（和方法直接对应），便于排障；</li>
 *   <li>没有第三方 proxy 的依赖。</li>
 * </ol>
 *
 * <p>使用：在 {@code MybatisConfig} 里通过 {@link org.apache.ibatis.session.Configuration#addInterceptor} 注册。</p>
 *
 * @author P0-refactor
 */
@Data
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        // MyBatis 3.5.x 没有 (MappedStatement, Object, RowBounds, CacheKey, BoundSql) 这个重载，
        // 必须带上 ResultHandler；否则 Plugin.getSignatureMap() 抛 NoSuchMethodException，
        // 任何一条 SQL 都会以 "Error opening session" 失败。
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "queryCursor",
                args = {MappedStatement.class, Object.class, RowBounds.class})
})
public class SlowSqlLogger implements Interceptor {

    /** 慢 SQL 阈值，默认 500ms */
    private long thresholdMs = 500L;

    /** 是否打印完整 SQL，true 时带参数 */
    private boolean printFullSql = true;

    /** 单条 SQL 参数序列化最大长度 */
    private int maxParamLength = 500;

    /** MDC 中 traceId 的 key（项目常用 "traceId"） */
    private String traceIdKey = "traceId";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.nanoTime();
        Throwable error = null;
        Object result;
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            if (costMs >= thresholdMs || error != null) {
                try {
                    formatLog(invocation, costMs, error);
                } catch (Exception ignore) {
                    // 监控失败不影响业务
                }
            }
        }
    }

    private void formatLog(Invocation invocation, long costMs, Throwable error) {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();
        SqlCommandType type = ms.getSqlCommandType();
        String traceId = MDC.get(traceIdKey);
        StringBuilder sb = new StringBuilder(512);
        sb.append("[SlowSql] cost=").append(costMs).append("ms")
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
        if (costMs >= thresholdMs || error != null) {
            log.warn(sb.toString());
        } else {
            log.info(sb.toString());
        }
    }

    /**
     * 把参数列表拼成可读形式
     */
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
    }
}
