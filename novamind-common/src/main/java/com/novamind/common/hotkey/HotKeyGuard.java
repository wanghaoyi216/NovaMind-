package com.novamind.common.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/**
 * <h1>R9 Hot Key 防护（本地缓存 + 多 key 分散）</h1>
 *
 * <p>针对热点读路径（如 {@code pay:order:{id}} / {@code coupon:{id}}）的雪崩与击穿风险，
 * 通过 Caffeine 本地缓存吸收大量同 key 读请求，并把缓存按 hash 分桶到 {@link #BUCKET_COUNT}
 * (16) 个独立 Cache 实例上，避免单 Cache 的并发竞争成为瓶颈。</p>
 *
 * <p><b>核心特性：</b></p>
 * <ul>
 *   <li><b>多 key 分桶</b>：对原始 key 做 FNV-1a hash 后取模 16，路由到独立 Caffeine Cache，
 *       把单点并发压力分散 16 倍。每个桶单独配置 size / TTL，互不污染。</li>
 *   <li><b>LoadingCache</b>：调用方提供 {@link Loader}，未命中时由一个线程执行加载，
 *       其他并发请求等待（避免击穿打 DB）。</li>
 *   <li><b>穿透保护</b>：{@code allowNull=false}（默认）时负向结果也缓存，
 *       防止恶意/随机 key 直接打 DB。</li>
 *   <li><b>Micrometer 指标</b>：暴露 {@code hotkey_bucket_hit / miss / load} 三类 Counter，
 *       按 bucket 维度打 tag，方便 Grafana 看板对热点桶做告警。</li>
 *   <li><b>线程安全</b>：{@link LongAdder} 统计命中数；{@link Cache#get} 内部已经做并发控制。</li>
 * </ul>
 *
 * <p><b>典型用法：</b></p>
 * <pre>{@code
 *     private final HotKeyGuard<Long, PayOrder> payOrderGuard =
 *         HotKeyGuard.<Long, PayOrder>builder()
 *             .name("pay:order")
 *             .maxSizePerBucket(5_000)
 *             .expireAfterWrite(Duration.ofMinutes(5))
 *             .allowNull(false)
 *             .build();
 *
 *     public PayOrder queryByBizOrderNo(Long bizOrderNo) {
 *         return payOrderGuard.get(bizOrderNo, id ->
 *             lambdaQuery().eq(PayOrder::getBizOrderNo, id).one());
 *     }
 * }</pre>
 *
 * <p>底层生成的真实缓存键形如 {@code pay:order:7:12345}，
 * 仍然保留了原始 key 的可读性，但分散到了 16 个独立 Cache 实例上。</p>
 *
 * @author R9-refactor
 */
@Slf4j
public class HotKeyGuard<K, V> {

    /**
     * 分桶数量。16 是 2 的幂，便于 {@code hash & (BUCKET_COUNT-1)} 位运算；
     * 同时在大多数场景下足以把热点 key 的并发压力分散到可接受水平。
     */
    public static final int BUCKET_COUNT = 16;

    /** 桶掩码，{@code hash & MASK} == {@code hash % BUCKET_COUNT}（当 BUCKET_COUNT 为 2 的幂） */
    private static final int MASK = BUCKET_COUNT - 1;

    /** 桶数组：每个桶是一个独立的 Caffeine Cache */
    @SuppressWarnings("unchecked")
    private final Cache<String, V>[] buckets = new Cache[BUCKET_COUNT];

    /** 桶级 hit / miss / load 计数器（按 bucket index 打 tag） */
    private final LongAdder[] hitCounters = new LongAdder[BUCKET_COUNT];
    private final LongAdder[] missCounters = new LongAdder[BUCKET_COUNT];
    private final LongAdder[] loadCounters = new LongAdder[BUCKET_COUNT];

    /** Micrometer Counter 数组（懒初始化，仅当 MeterRegistry 非空时填充） */
    private final Counter[] hitMicros;
    private final Counter[] missMicros;
    private final Counter[] loadMicros;

    /** 用于把 hit/miss/load 计数同步到 Micrometer 的注册表（可能为 null） */
    private final MeterRegistry meterRegistry;

    /** 业务前缀，例如 {@code pay:order} / {@code coupon}，便于排查日志与指标 */
    private final String name$;

    /** 每个桶的最大条目数 */
    private final long maxSizePerBucket;

    /** 写入后过期时间 */
    private final Duration expireAfterWrite;

    /** 是否允许把 null 结果缓存（默认 false，防止穿透） */
    private final boolean allowNull;

    private HotKeyGuard(Builder<K, V> b) {
        this.name$ = b.name;
        this.maxSizePerBucket = b.maxSizePerBucket;
        this.expireAfterWrite = b.expireAfterWrite;
        this.allowNull = b.allowNull;
        this.meterRegistry = b.meterRegistry;
        this.hitMicros = new Counter[BUCKET_COUNT];
        this.missMicros = new Counter[BUCKET_COUNT];
        this.loadMicros = new Counter[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            this.buckets[i] = Caffeine.newBuilder()
                    .maximumSize(b.maxSizePerBucket)
                    .expireAfterWrite(b.expireAfterWrite)
                    // 不允许 null key；value 由 allowNull 决定
                    .build();
            this.hitCounters[i] = new LongAdder();
            this.missCounters[i] = new LongAdder();
            this.loadCounters[i] = new LongAdder();
        }
        if (meterRegistry != null) {
            registerMetrics();
        }
        log.info("[HotKeyGuard] 已初始化, name={}, buckets={}, maxSizePerBucket={}, expire={}",
                name$, BUCKET_COUNT, maxSizePerBucket, expireAfterWrite);
    }

    /**
     * 入口方法：根据 key 读取 value，未命中时通过 {@link Loader} 加载并回填缓存。
     *
     * @param key    业务原始 key（如 {@code 12345L}）
     * @param loader 未命中时的加载器，只有一个线程会真正执行
     * @return 缓存或新加载的 value；如果加载结果为 null 且 {@code allowNull=false}，返回 null 但不缓存
     */
    public V get(K key, Loader<V> loader) {
        if (key == null) {
            return loader.load();
        }
        int idx = bucketIndex(key);
        String cacheKey = bucketKey(idx, key);
        Cache<String, V> bucket = buckets[idx];

        V cached = bucket.get(cacheKey, k -> {
            // Caffeine 在 cache miss 时调用此方法 -> 一定是一次 miss
            missCounters[idx].increment();
            incMiss(idx);
            V loaded = loader.load();
            if (loaded == null) {
                // null 场景：allowNull=true 时把 null 当成有效值缓存（穿透保护关闭），
                //              allowNull=false 时不缓存，每次都会重新打 loader（穿透保护开启）
                if (allowNull) {
                    loadCounters[idx].increment();
                    incLoad(idx);
                }
                return null;
            }
            loadCounters[idx].increment();
            incLoad(idx);
            return loaded;
        });
        if (cached != null) {
            hitCounters[idx].increment();
            incHit(idx);
            return cached;
        }
        // 走到这里：loader 返回 null（无论是否缓存）
        return null;
    }

    /**
     * 失效某个 key 下的所有桶（实际只可能在 1 个桶里，因为分桶 hash 是确定性的）。
     * 典型用法：写 DB 成功后清除缓存。
     */
    public void invalidate(K key) {
        if (key == null) {
            return;
        }
        int idx = bucketIndex(key);
        buckets[idx].invalidate(bucketKey(idx, key));
    }

    /** 清空所有桶。一般用于运维/测试。 */
    public void invalidateAll() {
        for (Cache<String, V> b : buckets) {
            b.invalidateAll();
        }
    }

    /**
     * 把原始 key 路由到具体的桶下标。
     * 使用 FNV-1a 32-bit，分布均匀且无需额外依赖。
     */
    private int bucketIndex(K key) {
        String s = String.valueOf(key);
        return (int) (fnv1a32(s) & MASK);
    }

    /** 拼接真实缓存键：{@code <name>:<bucketIdx>:<key>} */
    private String bucketKey(int idx, K key) {
        return name$ + ":" + idx + ":" + key;
    }

    private long fnv1a32(String s) {
        long hash = 0x811C9DC5L; // FNV offset basis
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x01000193L; // FNV prime
        }
        return hash & 0xFFFFFFFFL;
    }

    /** 注册 Micrometer Counter，按桶 index 打 tag */
    private void registerMetrics() {
        for (int i = 0; i < BUCKET_COUNT; i++) {
            Tags tags = Tags.of("name", name$, "bucket", String.valueOf(i));
            hitMicros[i] = Counter.builder("hotkey.bucket.hit").tags(tags).register(meterRegistry);
            missMicros[i] = Counter.builder("hotkey.bucket.miss").tags(tags).register(meterRegistry);
            loadMicros[i] = Counter.builder("hotkey.bucket.load").tags(tags).register(meterRegistry);
        }
    }

    private void incHit(int idx) {
        Counter c = hitMicros[idx];
        if (c != null) c.increment();
    }

    private void incMiss(int idx) {
        Counter c = missMicros[idx];
        if (c != null) c.increment();
    }

    private void incLoad(int idx) {
        Counter c = loadMicros[idx];
        if (c != null) c.increment();
    }

    /** 加载器：调用方提供从外部数据源加载 value 的逻辑 */
    @FunctionalInterface
    public interface Loader<V> {
        V load();
    }

    public String getName() {
        return name$;
    }

    /**
     * 测试/调试用：返回当前所有桶的 hit / miss / load 快照。
     */
    public long[] hitSnapshot() {
        return Arrays.stream(hitCounters).mapToLong(LongAdder::sum).toArray();
    }

    public long[] missSnapshot() {
        return Arrays.stream(missCounters).mapToLong(LongAdder::sum).toArray();
    }

    public long[] loadSnapshot() {
        return Arrays.stream(loadCounters).mapToLong(LongAdder::sum).toArray();
    }

    // ----------------- Builder -----------------

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {
        private String name = "hotkey";
        private long maxSizePerBucket = 10_000;
        private Duration expireAfterWrite = Duration.ofMinutes(5);
        private boolean allowNull = false;
        private MeterRegistry meterRegistry;

        public Builder<K, V> name(String n) {
            this.name = n;
            return this;
        }

        public Builder<K, V> maxSizePerBucket(long s) {
            this.maxSizePerBucket = s;
            return this;
        }

        public Builder<K, V> expireAfterWrite(Duration d) {
            this.expireAfterWrite = d;
            return this;
        }

        public Builder<K, V> allowNull(boolean v) {
            this.allowNull = v;
            return this;
        }

        public Builder<K, V> meterRegistry(MeterRegistry r) {
            this.meterRegistry = r;
            return this;
        }

        public HotKeyGuard<K, V> build() {
            return new HotKeyGuard<>(this);
        }
    }
}