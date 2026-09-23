package com.novamind.common.autoconfigure.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <h1>R15 HikariCP 统一参数绑定</h1>
 *
 * <p>对应 Nacos 配置：</p>
 * <pre>{@code
 * novamind:
 *   datasource:
 *     hikari:
 *       auto-pool-size: true              # 是否按 CPU 核数 * 2 + 1 自动算 pool size
 *       maximum-pool-size: 21             # 当 auto-pool-size=false 时生效
 *       minimum-idle: 5
 *       connection-timeout: 3000          # ms：拿不到连接的最长等待，默认 3s
 *       validation-timeout: 2000          # ms：连接活性校验超时，默认 2s
 *       max-lifetime: 1800000             # ms：连接最大生命周期 30min，必须 < MySQL wait_timeout
 *       idle-timeout: 600000              # ms：空闲连接驱逐
 *       leak-detection-threshold: 10000   # ms：连接泄漏阈值，默认 10s
 *       pool-name: HikariPool-Novamind
 *       auto-commit: true
 *       connection-test-query: null       # 留空走 JDBC4 isValid()
 * }</pre>
 *
 * <h3>调优公式</h3>
 * <p>{@code maximumPoolSize = cpu cores * 2 + 1}（PostgreSQL / HikariCP 官方推荐）。</p>
 *
 * @author R15-refactor
 */
@Data
@ConfigurationProperties(prefix = "novamind.datasource.hikari")
public class HikariProperties {

    /**
     * 是否按 {@code Runtime.availableProcessors() * 2 + 1} 自动算 pool size。
     * 默认 true；设为 false 后使用 {@link #maximumPoolSize} 字段。
     */
    private boolean autoPoolSize = true;

    /**
     * 当 {@link #autoPoolSize}=false 时生效；默认给一个 4 核机的安全值 21。
     */
    private int maximumPoolSize = 21;

    /** 最小空闲连接数（连接池预热） */
    private int minimumIdle = 5;

    /** 拿不到连接的最长等待（ms），默认 3s */
    private long connectionTimeout = 3_000L;

    /** 连接活性校验超时（ms），默认 2s */
    private long validationTimeout = 2_000L;

    /** 连接最大生命周期（ms），默认 30min，避免被服务端 wait_timeout 主动断开 */
    private long maxLifetime = 1_800_000L;

    /** 空闲连接驱逐时间（ms），默认 10min */
    private long idleTimeout = 600_000L;

    /** 连接泄漏阈值（ms），超过此时间未关闭则 WARN，默认 10s */
    private long leakDetectionThreshold = 10_000L;

    /** 连接池名称前缀，便于日志和监控识别 */
    private String poolName = "HikariPool-Novamind";

    /** 是否自动提交，false 时业务必须显式 commit */
    private boolean autoCommit = true;

    /**
     * 自定义活性校验 SQL；留空走 JDBC4 {@code Connection.isValid()}，推荐。
     * MySQL 场景下不建议设置 {@code SELECT 1}，会增加一次 RTT。
     */
    private String connectionTestQuery;

    /**
     * 根据当前 JVM 所在机器的 CPU 核数计算 pool size。
     * 公式：{@code cores * 2 + 1}，HikariCP Wiki 推荐值。
     *
     * @return 不小于 4 的 pool size（避免过小）
     */
    public int resolvePoolSize() {
        if (!autoPoolSize) {
            return Math.max(maximumPoolSize, 1);
        }
        int cores = Runtime.getRuntime().availableProcessors();
        int size = cores * 2 + 1;
        // 安全下限：太小的池反而成为瓶颈；同时防止容器化场景 cores=1 导致 size=3 不够
        return Math.max(size, 4);
    }
}