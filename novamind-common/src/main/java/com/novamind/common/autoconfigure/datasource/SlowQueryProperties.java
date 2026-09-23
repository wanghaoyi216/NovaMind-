package com.novamind.common.autoconfigure.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <h1>R15 慢查询拦截器参数绑定</h1>
 *
 * <p>对应 Nacos 配置：</p>
 * <pre>{@code
 * novamind:
 *   datasource:
 *     slow-query:
 *       enabled: true          # 总开关
 *       threshold-ms: 200      # 慢查询阈值
 *       warn-log-enabled: true # 是否打 WARN
 *       print-full-sql: true   # 是否打印完整 SQL
 *       max-param-length: 500  # 参数序列化最大长度
 *       trace-id-key: traceId  # MDC key
 * }</pre>
 *
 * @author R15-refactor
 */
@Data
@ConfigurationProperties(prefix = "novamind.datasource.slow-query")
public class SlowQueryProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 慢查询阈值（ms） */
    private long thresholdMs = SlowQueryInterceptor.DEFAULT_THRESHOLD_MS;

    /** 是否打 WARN 日志 */
    private boolean warnLogEnabled = true;

    /** 是否打印完整 SQL + 参数 */
    private boolean printFullSql = true;

    /** 单条 SQL 参数序列化最大长度 */
    private int maxParamLength = 500;

    /** MDC 中 traceId 的 key */
    private String traceIdKey = "traceId";
}