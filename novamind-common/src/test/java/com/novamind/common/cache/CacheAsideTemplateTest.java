package com.novamind.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * R23 CacheAsideTemplate 单元测试
 *
 * <p>用 Mockito 把 RedissonClient 行为完全仿真出来，覆盖四个核心场景：</p>
 * <ul>
 *   <li><b>击穿</b>：cache miss 时通过 Lua SETNX 抢锁，并发同 key 请求只回源一次。</li>
 *   <li><b>穿透</b>：Loader 返回 null 时写 NULL_MARKER 占位 + 短 TTL，后续读不打到 DB。</li>
 *   <li><b>雪崩</b>：写缓存的 TTL 自动 ±10% 抖动，避免大批 key 同时过期。</li>
 *   <li><b>一致性</b>：写 DB 后调 invalidate()，会把数据 key 和锁 key 都删掉。</li>
 * </ul>
 */
class CacheAsideTemplateTest {

    private RedissonClient redisson;
    private RScript script;
    @SuppressWarnings("rawtypes")
    private RBucket bucket;
    private CacheAsideTemplate cache;
    private ObjectMapper om;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisson = mock(RedissonClient.class);
        script = mock(RScript.class);
        bucket = mock(RBucket.class);
        when(redisson.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(redisson.getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(bucket);
        om = new ObjectMapper();
        cache = new CacheAsideTemplate(redisson, om, new CacheAsideProperties());
    }

    // ===================== 1. 击穿（Breakdown） =====================

    @Test
    @DisplayName("击穿：并发同 key 抢锁，只有一个线程回源 Loader")
    void breakdown_singleFlight() throws Exception {
        // 1) 模拟 Redis：第一次调用 Lua -> null（抢到锁，需要回源），
        //               第二次调用 Lua -> LOCKED_MARKER（其他线程在加载），
        //               第三次（其它线程重试）调 rawPut -> 返回 1
        //               其它线程 GET -> 返回已写入的 value
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
                anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    // 通过 ARGV 第一个值判断是哪个脚本：
                    // check-lock 脚本 ARGV 长度 = 1，put 脚本 ARGV 长度 = 2
                    Object[] argv = invocation.getArgument(4);
                    if (argv.length == 1) {
                        // check-lock：返回 null 表示抢到锁
                        return null;
                    }
                    return 1L;
                });
        // 重试时其它线程 GET -> 拿到值
        when(bucket.get()).thenReturn("\"loaded-value\"");

        AtomicInteger loaderCalls = new AtomicInteger(0);
        CacheAsideTemplate.Loader<String> loader = () -> {
            int n = loaderCalls.incrementAndGet();
            // 模拟 DB 慢查询（部署修复注记：Loader.load() 未声明受检异常，
            // lambda 内的 Thread.sleep 必须就地捕获，否则 testCompile 失败）
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return "loaded-" + n;
        };

        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // 第一个调用抢到锁（Lua 返回 null），其它调用返回 LOCKED_MARKER -> 走重试 GET
                    String v = cache.get("test:breakdown:1", String.class, loader, Duration.ofMinutes(1));
                    assertNotNull(v);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "并发线程未在 5s 内结束");
        pool.shutdownNow();

        // 抢到锁的线程调 1 次 Loader；其它线程走 waitForOtherThread 路径，不会调 Loader
        assertEquals(1, loaderCalls.get(),
                "击穿：所有并发线程应共享 1 次回源结果，实际 = " + loaderCalls.get());
    }

    // ===================== 2. 穿透（Penetration） =====================

    @Test
    @DisplayName("穿透：Loader 返回 null 时写入 NULL_MARKER 占位")
    void penetration_nullCached() {
        // 第一次 check-lock 抢到锁（返回 null），写缓存（返回 1）
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
                anyList(), any(Object[].class)))
                .thenReturn(null)  // check-lock -> 抢到锁
                .thenReturn(1L);   // put       -> 写 NULL_MARKER

        AtomicInteger loaderCalls = new AtomicInteger(0);
        CacheAsideTemplate.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return null;
        };

        // 第一次：miss + 抢锁 + 加载 null + 写 NULL_MARKER
        String v1 = cache.get("test:pen:1", String.class, loader, Duration.ofMinutes(1));
        assertNull(v1);
        assertEquals(1, loaderCalls.get());

        // 验证 put 时写入的是 NULL_MARKER
        ArgumentCaptor<Object> argvCaptor = ArgumentCaptor.forClass(Object.class);
        verify(script, atLeastOnce()).eval(
                eq(RScript.Mode.READ_WRITE),
                argThat(s -> s.contains("redis.call('SET'") && s.contains("redis.call('DEL'")),
                eq(RScript.ReturnType.INTEGER),
                eq(Arrays.asList("test:pen:1", "test:pen:1:lock")),
                argvCaptor.capture(),
                any());
        // 第一次 ARGV[0] = NULL_MARKER
        assertEquals(CacheAsideTemplate.NULL_MARKER, argvCaptor.getAllValues().get(0));
    }

    @Test
    @DisplayName("穿透：NULL_MARKER 命中时直接返回 null，不调 Loader")
    void penetration_nullHitShortCircuit() {
        // check-lock 直接返回 NULL_MARKER（已有空值缓存）
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
                anyList(), any(Object[].class)))
                .thenReturn(CacheAsideTemplate.NULL_MARKER);

        AtomicInteger loaderCalls = new AtomicInteger(0);
        CacheAsideTemplate.Loader<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return "should-not-be-called";
        };

        String v = cache.get("test:pen:hit", String.class, loader, Duration.ofMinutes(1));
        assertNull(v, "命中 NULL_MARKER 时应返回 null");
        assertEquals(0, loaderCalls.get(), "命中 NULL_MARKER 时 Loader 不应被调用");
    }

    // ===================== 3. 雪崩（Avalanche） =====================

    @Test
    @DisplayName("雪崩：相同 base TTL 多次写入，实际 TTL 在 ±10% 内随机")
    void avalanche_randomTtl() {
        // 所有 eval 都成功
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
                anyList(), any(Object[].class))).thenReturn(1L);

        Duration base = Duration.ofMinutes(5); // 300s
        long baseSec = base.toSeconds();
        int samples = 100;
        long minSeen = Long.MAX_VALUE, maxSeen = Long.MIN_VALUE;

        for (int i = 0; i < samples; i++) {
            cache.put("test:avalanche:" + i, "v-" + i, base);
        }

        // 抓所有写缓存时传给 Redis 的 TTL（ARGV[1]）
        ArgumentCaptor<String> ttlCaptor = ArgumentCaptor.forClass(String.class);
        verify(script, atLeast(samples)).eval(
                eq(RScript.Mode.READ_WRITE),
                argThat(s -> s.contains("redis.call('SET'")),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                ttlCaptor.capture()
        );
        for (String ttlStr : ttlCaptor.getAllValues()) {
            long ttl = Long.parseLong(ttlStr);
            minSeen = Math.min(minSeen, ttl);
            maxSeen = Math.max(maxSeen, ttl);
        }
        long lower = baseSec * 9 / 10; // -10%
        long upper = baseSec * 11 / 10; // +10%
        // 100 次随机不可能所有值都贴边，所以断言 TTL 落在 [lower, upper] 区间
        assertTrue(minSeen >= lower && maxSeen <= upper,
                "TTL 应在 ±10% 抖动区间内，实际 [" + minSeen + ", " + maxSeen + "]");
        // 必须有抖动：最大值 > 最小值
        assertTrue(maxSeen > minSeen,
                "应有抖动，实际 min=" + minSeen + " max=" + maxSeen);
    }

    // ===================== 4. 一致性（Consistency） =====================

    @Test
    @DisplayName("一致性：invalidate 删除数据 key 和锁 key，第二次读会重新回源")
    void consistency_invalidateReloads() {
        // 第一次：miss 抢锁回源；写缓存成功
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
                anyList(), any(Object[].class)))
                .thenReturn(null)   // 1) check-lock 抢到锁
                .thenReturn(1L)     // 2) put 成功
                .thenReturn(null)   // 3) 第二次读 check-lock 抢到锁（因为 invalidate 删了）
                .thenReturn(1L);    // 4) 第二次读 put 成功
        when(bucket.get()).thenReturn(null);

        AtomicInteger loaderCalls = new AtomicInteger(0);
        CacheAsideTemplate.Loader<String> loader = () -> {
            int n = loaderCalls.incrementAndGet();
            return "v-" + n;
        };

        // 第一次读：触发回源
        String v1 = cache.get("test:consistency:1", String.class, loader, Duration.ofMinutes(1));
        assertEquals("v-1", v1);

        // 模拟「写 DB → 删缓存」
        cache.invalidate("test:consistency:1");
        verify(bucket, atLeastOnce()).delete();
        // 数据 key 和锁 key 都应被删
        verify(redisson).getBucket("test:consistency:1", StringCodec.INSTANCE);
        verify(redisson).getBucket("test:consistency:1:lock", StringCodec.INSTANCE);

        // 第二次读：cache miss → 重新回源
        String v2 = cache.get("test:consistency:1", String.class, loader, Duration.ofMinutes(1));
        assertEquals("v-2", v2);
        assertEquals(2, loaderCalls.get(), "invalidate 后应重新调 Loader");
    }

    @Test
    @DisplayName("一致性：invalidatePattern 走 SCAN+UNLINK 删 key")
    void consistency_invalidatePattern() {
        // 第一次 SCAN 返回 [cursor, keys]
        List<Object> scanResult1 = Arrays.asList("0", Arrays.asList("pay:order:1", "pay:order:2"));
        List<Object> scanResult2 = Arrays.asList("0", Collections.emptyList());
        when(script.eval(eq(RScript.Mode.READ_ONLY), anyString(), eq(RScript.ReturnType.MULTI),
                eq(Collections.emptyList()), any(Object[].class)))
                .thenReturn(scanResult1, scanResult2);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                eq(Collections.emptyList()), any(Object[].class)))
                .thenReturn(2L);

        long deleted = cache.invalidatePattern("pay:order:*");
        assertEquals(2L, deleted);
        // 验证 SCAN 用了 pattern 参数
        ArgumentCaptor<Object[]> argvCap = ArgumentCaptor.forClass(Object[].class);
        verify(script, atLeastOnce()).eval(
                eq(RScript.Mode.READ_ONLY), anyString(), eq(RScript.ReturnType.MULTI),
                eq(Collections.emptyList()),
                argvCap.capture()
        );
        // argv 至少包含 pattern 参数
        boolean foundPattern = argvCap.getAllValues().stream()
                .flatMap(Arrays::stream)
                .anyMatch(a -> "pay:order:*".equals(a));
        assertTrue(foundPattern, "SCAN 应传 pattern=pay:order:*");
    }
}
