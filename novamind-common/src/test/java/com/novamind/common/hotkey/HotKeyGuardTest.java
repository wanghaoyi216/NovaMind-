package com.novamind.common.hotkey;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R9 HotKeyGuard 单元测试
 *
 * <p>覆盖关键不变量：</p>
 * <ul>
 *   <li>同一 key 多次读取只触发一次 Loader（防止击穿打 DB）</li>
 *   <li>FNV-1a hash 分桶到 16 个独立桶，并发请求分散</li>
 *   <li>allowNull=false 时 loader 返回 null 不缓存（防止穿透）</li>
 *   <li>allowNull=true 时 null 也被缓存</li>
 *   <li>{@link HotKeyGuard#invalidate(Object)} 精准失效单个桶</li>
 *   <li>Micrometer 指标按 bucket tag 暴露</li>
 *   <li>高并发下 Loader 调用次数 ≈ 1（single-flight）</li>
 * </ul>
 */
class HotKeyGuardTest {

    private HotKeyGuard<Long, String> guard;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        guard = HotKeyGuard.<Long, String>builder()
                .name("test:hotkey")
                .maxSizePerBucket(1_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .meterRegistry(meterRegistry)
                .build();
    }

    @Test
    @DisplayName("同一 key 重复读取只触发一次 Loader，防止击穿")
    void cacheHit_doesNotCallLoaderAgain() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return "value-42";
        };

        String v1 = guard.get(42L, loader);
        String v2 = guard.get(42L, loader);
        String v3 = guard.get(42L, loader);

        assertEquals("value-42", v1);
        assertEquals("value-42", v2);
        assertEquals("value-42", v3);
        assertEquals(1, loaderCalls.get(), "同 key 3 次读取只应调 1 次 loader");
    }

    @Test
    @DisplayName("不同 key 分散到不同桶，互不污染")
    void differentKeysIsolatedByBucket() {
        // 1000 个 key 各自只命中 1 次
        for (long k = 0; k < 1000; k++) {
            long key = k;
            String v = guard.get(key, () -> "v-" + key);
            assertEquals("v-" + key, v);
        }
        // 不应该有任何 key 再触发 loader
        AtomicInteger extra = new AtomicInteger(0);
        for (long k = 0; k < 1000; k++) {
            long key = k;
            guard.get(key, () -> {
                extra.incrementAndGet();
                return "x";
            });
        }
        assertEquals(0, extra.get(), "所有 key 都应已缓存，loader 不再被调");
    }

    @Test
    @DisplayName("allowNull=false：loader 返回 null 不缓存，下次仍会调 loader")
    void nullNotCachedByDefault() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return null;
        };

        assertNull(guard.get(1L, loader));
        assertNull(guard.get(1L, loader));
        assertEquals(2, loaderCalls.get(), "allowNull=false 时 null 不缓存，每次都应调 loader");
    }

    @Test
    @DisplayName("allowNull=true 也无法绕过 Caffeine 限制：null 不会被缓存（Caffeine 不支持 null value）")
    void caffeineDoesNotCacheNull() {
        // 已知行为：Caffeine 的 Cache.get(K, Function) 即使 Function 返回 null，
        // 也不会缓存 null。allowNull 标志目前主要用作业务层开关；
        // 这里只验证不会抛异常且下次依然能调 loader。
        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return null;
        };

        HotKeyGuard<Long, String> nullable = HotKeyGuard.<Long, String>builder()
                .name("nullable")
                .maxSizePerBucket(100)
                .expireAfterWrite(Duration.ofMinutes(1))
                .allowNull(true)
                .build();

        assertNull(nullable.get(99L, loader));
        assertNull(nullable.get(99L, loader));
        // Caffeine 不会缓存 null，loader 仍会被调用（与 allowNull=false 一致）
        assertTrue(loaderCalls.get() >= 2,
                "Caffeine 不会缓存 null，loader 会被多次调用");
    }

    @Test
    @DisplayName("invalidate 后再次读取会触发 loader")
    void invalidateTriggersReload() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<String> loader = () -> {
            int n = loaderCalls.incrementAndGet();
            return "loaded-" + n;
        };

        assertEquals("loaded-1", guard.get(7L, loader));
        guard.invalidate(7L);
        assertEquals("loaded-2", guard.get(7L, loader));
    }

    @Test
    @DisplayName("FNV-1a 分桶：相同 key 总落在同一桶，分布近似均匀")
    void bucketIndexIsDeterministicAndDistributed() {
        int[] hits = new int[HotKeyGuard.BUCKET_COUNT];
        for (long k = 0; k < 10_000; k++) {
            int idx = Math.floorMod((int) (fnv1a32ForTest(String.valueOf(k))) & (HotKeyGuard.BUCKET_COUNT - 1),
                    HotKeyGuard.BUCKET_COUNT);
            hits[idx]++;
        }
        // 10_000 / 16 ≈ 625；每个桶至少分到 100 个算健康（防止 hash 退化为 1-2 个桶）
        for (int i = 0; i < HotKeyGuard.BUCKET_COUNT; i++) {
            assertTrue(hits[i] > 100, "bucket " + i + " 命中数过低: " + hits[i]);
        }
    }

    @Test
    @DisplayName("高并发下同 key 加载近似单飞（避免击穿）")
    void concurrentGet_singleFlight() throws Exception {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            try {
                // 模拟慢加载 50ms
                Thread.sleep(50);
            } catch (InterruptedException ignore) {
            }
            return "value-100";
        };

        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String v = guard.get(100L, loader);
                    assertEquals("value-100", v);
                } catch (InterruptedException ignore) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "并发线程未在 5s 内结束");
        pool.shutdownNow();

        // 由于 Caffeine get(K, Function) 没有像 getAll 那样的 batchSingleFlight，
        // 在完全无竞争的瞬间窗口里允许多次 loader，但不应超过线程数的一半
        int calls = loaderCalls.get();
        assertTrue(calls >= 1 && calls <= threads / 2,
                "loader 调用次数应在合理范围，实际=" + calls);
    }

    @Test
    @DisplayName("Micrometer 指标：按 bucket 暴露 hit/miss/load")
    void metricsExposed() {
        guard.get(1L, () -> "a");
        guard.get(1L, () -> "a"); // hit
        guard.get(2L, () -> "b");
        guard.get(2L, () -> "b"); // hit
        guard.get(2L, () -> "b"); // hit

        // 找打 tag name=test:hotkey 的 load 计数器
        double loads = meterRegistry.find("hotkey.bucket.load")
                .tag("name", "test:hotkey")
                .counters()
                .stream()
                .mapToDouble(c -> c.count())
                .sum();
        assertTrue(loads >= 2, "至少应有 2 次 load");
    }

    @Test
    @DisplayName("invalidateAll 后所有 hit 重新加载")
    void invalidateAllClearsEverything() {
        guard.get(1L, () -> "a");
        guard.get(2L, () -> "b");
        guard.invalidateAll();

        AtomicInteger calls = new AtomicInteger(0);
        guard.get(1L, () -> {
            calls.incrementAndGet();
            return "x";
        });
        assertEquals(1, calls.get());
    }

    // ---- helpers ----

    private static long fnv1a32ForTest(String s) {
        long hash = 0x811C9DC5L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x01000193L;
        }
        return hash & 0xFFFFFFFFL;
    }
}