package com.novamind.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * <h1>R23 Cache-Aside 模式统一封装</h1>
 *
 * <p>对外暴露 "读 miss 加载 DB 写缓存 / 写 DB 删缓存" 的标准 Cache-Aside 语义，
 * 内部通过 Redisson + Lua 脚本保证「查锁 + 写值」的原子性，
 * 并同时实现 <b>穿透</b>、<b>击穿</b>、<b>雪崩</b> 三大防御。</p>
 *
 * <h2>核心设计</h2>
 * <ol>
 *   <li><b>读路径（Cache-Aside Read）</b>
 *     <pre>
 *       ┌─ Lua 原子脚本（GET 数据 key / 失败则 SETNX 锁 key）
 *       │     ├─ 返回值           → 反序列化，命中
 *       │     ├─ 返回 LOCKED 占位 → 等待 / 重试
 *       │     └─ 返回空           → 调 Loader 加载
 *       │                              ├─ 加载成功 → 写缓存 + 释放锁
 *       │                              └─ 加载 null → 写空值占位（防穿透） + 释放锁
 *       └─
 *     </pre>
 *   </li>
 *   <li><b>写路径（Cache-Aside Write）</b>：先写 DB，再 {@link #invalidate(String)} 删缓存；
 *       不在写路径里直接 set 缓存，避免「DB 与缓存不一致」时缓存长期覆盖 DB。</li>
 *   <li><b>穿透防护</b>：Loader 返回 null 时，把 {@link #NULL_MARKER} 写入 Redis ，
 *       并以一个比正常 TTL 短得多的 {@link CacheAsideProperties#getNullTtl()} 兜底，
 *       防止恶意/随机 key 反复打 DB。</li>
 *   <li><b>击穿防护</b>：cache miss 后，Lua 脚本用 {@code SET key value NX EX} 抢锁；
 *       抢到锁的线程负责加载 DB，其它线程在 {@link #get(String, Class, Loader, Duration)}
 *       内部短暂 sleep + 重试，避免雪崩式回源。</li>
 *   <li><b>雪崩防护</b>：实际写入 Redis 的 TTL = {@code baseTtl * (1 ± jitter)}，
 *       让大量 key 不在同一时刻过期，避免缓存层集体 miss 把 DB 打挂。</li>
 * </ol>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 *     @Autowired
 *     private CacheAsideTemplate cache;
 *
 *     public PayOrder queryByBizOrderNo(Long bizOrderNo) {
 *         return cache.get("pay:order:" + bizOrderNo, PayOrder.class,
 *                 () -> lambdaQuery().eq(PayOrder::getBizOrderNo, bizOrderNo).one(),
 *                 Duration.ofMinutes(5));
 *     }
 *
 *     // 写 DB 后删缓存
 *     public boolean markPayOrderSuccess(Long id) {
 *         boolean ok = lambdaUpdate()...update();
 *         if (ok) {
 *             PayOrder po = getById(id);
 *             cache.invalidate("pay:order:" + po.getBizOrderNo());
 *         }
 *         return ok;
 *     }
 * }</pre>
 *
 * <h2>为什么用 Lua 脚本？</h2>
 * <p>普通 Java 代码做「先 GET 再 SET」会有竞态：
 *   T1 GET 看到 null 准备加载，T2 GET 也看到 null 准备加载 → 两个线程都打 DB。
 *   Lua 在 Redis 单线程内把「GET ＋ SETNX 锁」做成原子操作，
 *   保证「只有一个线程能拿到锁 → 只有一个线程去加载 DB」，
 *   这是 Cache-Aside 防击穿的标准做法。</p>
 *
 * <h2>与 @Cacheable 的差异</h2>
 * <ul>
 *   <li>{@code @Cacheable} 基于 Spring AOP 代理，方法内自调用会失效；本类是普通对象，调用方完全控制。</li>
 *   <li>{@code @Cacheable} 默认不会缓存 null 也不会随机 TTL；本类已默认开启三种防御。</li>
 *   <li>{@code @Cacheable} 的 key/value 序列化由 Spring Cache 抽象决定；本类用 {@link ObjectMapper}，
 *       业务可注入自定义 ObjectMapper 替换 JSON 规则。</li>
 * </ul>
 *
 * @author R23-refactor
 */
@Slf4j
public class CacheAsideTemplate {

    /**
     * 空值占位符：Loader 返回 null 时往 Redis 写这个特殊字符串。
     * 必须满足两个条件：① 不可能与业务正常 JSON 冲突；② 反序列化时能被识别为 null。
     */
    public static final String NULL_MARKER = "\0__NULL__\0";

    /** Lua 脚本：lock 路径下当值还不存在时返回的占位字符串 */
    private static final String LOCKED_MARKER = "\0__LOCKED__\0";

    /** 默认的抢锁等待 / 重试次数 */
    private static final int DEFAULT_LOCK_WAIT_TIMES = 20;

    /** 每次重试间隔（ms） */
    private static final long DEFAULT_LOCK_WAIT_INTERVAL_MS = 25L;

    /**
     * Lua 脚本：原子完成「查数据 + 没查到就抢锁」。
     * <p>KEYS[1] = 数据 key，KEYS[2] = 锁 key</p>
     * <p>ARGV[1] = 锁 TTL（秒）</p>
     * <p>返回：</p>
     * <ul>
     *   <li>已有值（字符串）→ 命中</li>
     *   <li>{@link #NULL_MARKER} → 命中（业务侧返回 null）</li>
     *   <li>{@link #LOCKED_MARKER} → 其他线程正在加载</li>
     *   <li>{@code false} → 没值且抢到锁，本线程负责加载</li>
     * </ul>
     */
    private static final String LUA_CHECK_AND_LOCK = ""
            + "local data = redis.call('GET', KEYS[1]) "
            + "if data then return data end "
            + "local ok = redis.call('SET', KEYS[2], '1', 'NX', 'EX', tonumber(ARGV[1])) "
            + "if ok then return false end "
            + "return '" + LOCKED_MARKER + "'";

    /**
     * Lua 脚本：原子完成「写数据 + 释放锁」。
     * <p>KEYS[1] = 数据 key，KEYS[2] = 锁 key</p>
     * <p>ARGV[1] = 序列化后的值，ARGV[2] = TTL（秒）</p>
     * <p>用 SET ... EX 而非 SETEX，方便后续做"如果 key 不存在才写"的扩展。</p>
     */
    private static final String LUA_PUT_AND_UNLOCK = ""
            + "redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2])) "
            + "redis.call('DEL', KEYS[2]) "
            + "return 1";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CacheAsideProperties properties;

    public CacheAsideTemplate(RedissonClient redissonClient,
                              ObjectMapper objectMapper,
                              CacheAsideProperties properties) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // ============================ 对外 API ============================

    /**
     * 标准 Cache-Aside 读：先查 Redis，miss 后回源 Loader 加载，再回写 Redis。
     *
     * @param key    业务 key（建议带命名空间前缀，如 {@code pay:order:123}）
     * @param type   反序列化目标类型
     * @param loader miss 时回源加载器
     * @param ttl    写入 Redis 的基础 TTL（实际 TTL = ttl ± jitter）
     * @param <T>    业务对象类型
     * @return 缓存值或新加载值；Loader 返回 null 时返回 null（但会被空值占位）
     */
    public <T> T get(String key, Class<T> type, Loader<T> loader, Duration ttl) {
        return get(key, type, loader, ttl, null);
    }

    /**
     * 同 {@link #get(String, Class, Loader, Duration)}，但支持泛型类型（List&lt;X&gt; / Map 等）。
     */
    public <T> T get(String key, TypeReference<T> typeRef, Loader<T> loader, Duration ttl) {
        if (typeRef == null) {
            throw new IllegalArgumentException("typeRef 不能为空");
        }
        Supplier<String> readFromCache = () -> {
            CheckResult r = checkAndLock(key);
            return r == null ? null : r.value;
        };
        return doGet(key, typeRef, loader, ttl, readFromCache);
    }

    /**
     * 把 value 写入缓存（带随机 TTL 抖动），常用于「主动预热」或「写后回填」场景。
     * 注意：标准 Cache-Aside 写路径是「先写 DB → 删缓存」，不是直接 put，
     * 但在某些一致性要求不高的场景下也可以用 put 代替 invalidate。
     */
    public void put(String key, Object value, Duration ttl) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (value == null) {
            log.debug("[CacheAside] put({}) 收到 null，改为写入 NULL_MARKER，TTL={}", key, properties.getNullTtl());
            rawPut(key, NULL_MARKER, properties.getNullTtl());
            return;
        }
        long ttlSec = jitterTtl(ttl);
        String json = serialize(value);
        rawPut(key, json, Duration.ofSeconds(ttlSec));
    }

    /**
     * 精准删除一个 key（Cache-Aside 写路径的标准动作）。
     * 不存在时静默成功。
     */
    public void invalidate(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            redissonClient.getBucket(key, StringCodec.INSTANCE).delete();
            // 顺手把锁也删掉，避免遗留锁阻塞下一次 miss
            redissonClient.getBucket(lockKey(key), StringCodec.INSTANCE).delete();
            log.debug("[CacheAside] invalidate({}) OK", key);
        } catch (Exception e) {
            log.warn("[CacheAside] invalidate({}) 失败: {}", key, e.getMessage());
        }
    }

    /**
     * 按 pattern 批量删除（{@code keys*} / {@code h*world} 等 glob 风格）。
     * <p>实现：{@code SCAN} 分批拉取 → {@code UNLINK} 删除，避免大 key 阻塞 Redis。</p>
     * <p>警告：生产环境请务必带上明确前缀（如 {@code pay:order:*}），不要用 {@code *} 全扫。</p>
     *
     * @return 实际删除的 key 数量
     */
    public long invalidatePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0L;
        }
        long deleted = 0L;
        try {
            // Redisson 的 RKeys.getKeysByPattern 在 cluster 模式下会被禁止，所以用底层 RScript 跑 SCAN
            // 这里走 StringCodec + RScript.eval 的方式保持和上面脚本一致
            String cursor = "0";
            do {
                List<Object> result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                        RScript.Mode.READ_ONLY,
                        "local c = redis.call('SCAN', ARGV[1], 'MATCH', ARGV[2], 'COUNT', 200) " +
                                "return {c[1], c[2]}",
                        RScript.ReturnType.MULTI,
                        Collections.emptyList(),
                        cursor, pattern
                );
                if (result == null || result.size() < 2) break;
                cursor = String.valueOf(result.get(0));
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) result.get(1);
                if (keys != null && !keys.isEmpty()) {
                    Long n = redissonClient.getScript(StringCodec.INSTANCE).eval(
                            RScript.Mode.READ_WRITE,
                            "return redis.call('UNLINK', unpack(ARGV))",
                            RScript.ReturnType.INTEGER,
                            Collections.emptyList(),
                            keys.toArray()
                    );
                    if (n != null) deleted += n;
                }
            } while (!"0".equals(cursor));
            log.info("[CacheAside] invalidatePattern({}) 删除 {} 个 key", pattern, deleted);
        } catch (Exception e) {
            log.warn("[CacheAside] invalidatePattern({}) 失败: {}", pattern, e.getMessage());
        }
        return deleted;
    }

    // ============================ 内部实现 ============================

    /** 重载：处理 Class 类型 */
    private <T> T get(String key, Class<T> type, Loader<T> loader, Duration ttl, Void ignored) {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空");
        }
        Supplier<String> readFromCache = () -> {
            CheckResult r = checkAndLock(key);
            return r == null ? null : r.value;
        };
        return doGet(key, type, loader, ttl, readFromCache);
    }

    /**
     * 真正的读路径：cache hit / locked-wait / cache miss+load 三种分支。
     */
    @SuppressWarnings("unchecked")
    private <T> T doGet(String key, Object typeInfo, Loader<T> loader, Duration ttl,
                        Supplier<String> readFromCache) {
        // 1) 一次原子查 + 抢锁
        CheckResult r = checkAndLock(key);
        if (r != null) {
            return (T) deserialize(r.value, typeInfo);
        }
        // r == null：抢到锁，本线程负责加载
        try {
            log.debug("[CacheAside] miss + 抢锁成功，本线程回源: {}", key);
            T loaded = loader == null ? null : loader.load();
            if (loaded == null) {
                // 防穿透：写 NULL_MARKER，TTL 用较短的 nullTtl
                rawPut(key, NULL_MARKER, properties.getNullTtl());
                return null;
            }
            // 命中回源成功：写缓存 + 释放锁
            long ttlSec = jitterTtl(ttl);
            String json = serialize(loaded);
            rawPut(key, json, Duration.ofSeconds(ttlSec));
            return loaded;
        } catch (RuntimeException ex) {
            // 回源失败：释放锁（让其他线程能再尝试），不写缓存
            safeUnlock(key);
            throw ex;
        } finally {
            // 双保险：上面的 rawPut 已经 DEL 了锁；如果走到 catch 路径，再 DEL 一次
            safeUnlock(key);
        }
    }

    /**
     * 调用 Lua 完成「查数据 + 抢锁」。
     *
     * @return null = 抢到锁需回源；非 null = 已命中（其 value 字段是序列化串，可能是 NULL_MARKER）
     */
    private CheckResult checkAndLock(String key) {
        // 先尝试一次原子查锁
        Object first = evalScript(LUA_CHECK_AND_LOCK, key, String.valueOf(properties.getLockTtl().toSeconds()));
        if (first == null) {
            return null; // 抢到锁
        }
        String val = String.valueOf(first);
        if (LOCKED_MARKER.equals(val)) {
            // 其它线程正在加载，短暂 sleep + 重试
            return waitForOtherThread(key);
        }
        if (NULL_MARKER.equals(val)) {
            return new CheckResult(val); // 命中空值
        }
        return new CheckResult(val);
    }

    /**
     * 其它线程正在加载时的等待逻辑：每隔 {@link #DEFAULT_LOCK_WAIT_INTERVAL_MS} 重新 GET 一次，
     * 直到取到值或达到最大重试次数。
     */
    private CheckResult waitForOtherThread(String key) {
        int maxRetry = properties.getLockWaitTimes() > 0
                ? properties.getLockWaitTimes() : DEFAULT_LOCK_WAIT_TIMES;
        long interval = properties.getLockWaitIntervalMs() > 0
                ? properties.getLockWaitIntervalMs() : DEFAULT_LOCK_WAIT_INTERVAL_MS;
        for (int i = 0; i < maxRetry; i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Object obj = redissonClient.getBucket(key, StringCodec.INSTANCE).get();
            if (obj != null) {
                String s = String.valueOf(obj);
                if (NULL_MARKER.equals(s)) {
                    return new CheckResult(s);
                }
                return new CheckResult(s);
            }
        }
        // 等不到（其它线程可能 load 失败），本线程兜底再加载一次
        log.warn("[CacheAside] 等锁超时，本线程兜底加载: {}", key);
        return null;
    }

    /** 调用 put+unlock 的 Lua */
    private void rawPut(String key, String value, Duration ttl) {
        long ttlSec = Math.max(1, ttl.toSeconds());
        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                LUA_PUT_AND_UNLOCK,
                RScript.ReturnType.INTEGER,
                Arrays.asList(key, lockKey(key)),
                value, String.valueOf(ttlSec)
        );
    }

    /** 异常路径下释放锁（best-effort） */
    private void safeUnlock(String key) {
        try {
            redissonClient.getBucket(lockKey(key), StringCodec.INSTANCE).delete();
        } catch (Exception ignore) {
        }
    }

    private Object evalScript(String script, String key, String lockTtlArg) {
        // 部署修复注记：redisson 3.13.6（根 pom 锁定版本）的 RScript.ReturnType
        // 没有 OBJECT（较新版本才引入），原代码无法编译。本脚本返回值为字符串
        // （缓存值/标记），调用方以 String.valueOf 消费，故用等价的 VALUE。
        return redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.VALUE,
                Arrays.asList(key, lockKey(key)),
                lockTtlArg
        );
    }

    private String lockKey(String key) {
        return key + ":lock";
    }

    /** 实际 TTL = base ± jitter% */
    private long jitterTtl(Duration base) {
        long baseSec = Math.max(1, base.toSeconds());
        int jitter = properties.getTtlJitterPercent();
        if (jitter <= 0) {
            return baseSec;
        }
        // 在 [-jitter%, +jitter%] 之间随机
        int delta = ThreadLocalRandom.current().nextInt(-jitter, jitter + 1);
        long ttl = baseSec * (100L + delta) / 100L;
        return Math.max(1, ttl);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("[CacheAside] 序列化失败: " + o, e);
        }
    }

    private Object deserialize(String json, Object typeInfo) {
        if (json == null || NULL_MARKER.equals(json)) {
            return null;
        }
        try {
            if (typeInfo instanceof Class<?>) {
                return objectMapper.readValue(json, (Class<?>) typeInfo);
            } else if (typeInfo instanceof TypeReference<?>) {
                return objectMapper.readValue(json, (TypeReference<?>) typeInfo);
            }
            throw new IllegalStateException("未知 typeInfo: " + typeInfo.getClass());
        } catch (Exception e) {
            throw new IllegalStateException("[CacheAside] 反序列化失败: " + json, e);
        }
    }

    /** 检查结果包装 */
    private static final class CheckResult {
        final String value;
        CheckResult(String value) { this.value = value; }
    }

    /** 业务侧加载器，与 HotKeyGuard.Loader 风格保持一致 */
    @FunctionalInterface
    public interface Loader<T> {
        T load();
    }
}
