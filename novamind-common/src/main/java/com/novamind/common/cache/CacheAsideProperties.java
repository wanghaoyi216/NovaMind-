package com.novamind.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * <h1>R23 CacheAsideTemplate 可调参数</h1>
 *
 * <p>所有参数都有合理默认值，业务侧无需配置即可使用；
 * 需要定制时可在 application.yml 中覆盖：</p>
 * <pre>
 * novamind:
 *   cache:
 *     null-ttl: 30s
 *     lock-ttl: 5s
 *     lock-wait-times: 20
 *     lock-wait-interval-ms: 25
 *     ttl-jitter-percent: 10
 * </pre>
 */
@ConfigurationProperties(prefix = "novamind.cache")
public class CacheAsideProperties {

    /**
     * 防穿透用的「空值占位」TTL。
     * 默认 30s：够短，让"暂时不存在的 key"很快能再次查；够长，能挡住瞬时穿透。
     */
    private Duration nullTtl = Duration.ofSeconds(30);

    /**
     * 单飞锁的 TTL。抢到锁的线程如果在这个时间内没完成 DB 加载并回写，
     * 锁会过期，下一个等待者兜底加载。
     */
    private Duration lockTtl = Duration.ofSeconds(5);

    /**
     * 没抢到锁的线程，GET key 的最大重试次数。
     */
    private int lockWaitTimes = 20;

    /**
     * 每次重试间隔（毫秒）。
     */
    private long lockWaitIntervalMs = 25;

    /**
     * 写入 Redis 的 TTL 抖动百分比。实际 TTL = base * (1 ± jitter%)。
     * 10 表示 ±10% 抖动。建议 5~20。
     */
    private int ttlJitterPercent = 10;

    public Duration getNullTtl() { return nullTtl; }
    public void setNullTtl(Duration nullTtl) { this.nullTtl = nullTtl; }

    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration lockTtl) { this.lockTtl = lockTtl; }

    public int getLockWaitTimes() { return lockWaitTimes; }
    public void setLockWaitTimes(int lockWaitTimes) { this.lockWaitTimes = lockWaitTimes; }

    public long getLockWaitIntervalMs() { return lockWaitIntervalMs; }
    public void setLockWaitIntervalMs(long lockWaitIntervalMs) { this.lockWaitIntervalMs = lockWaitIntervalMs; }

    public int getTtlJitterPercent() { return ttlJitterPercent; }
    public void setTtlJitterPercent(int ttlJitterPercent) { this.ttlJitterPercent = ttlJitterPercent; }
}
