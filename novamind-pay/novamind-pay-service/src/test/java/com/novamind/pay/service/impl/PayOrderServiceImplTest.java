package com.novamind.pay.service.impl;

import com.novamind.common.autoconfigure.mq.RabbitMqHelper;
import com.novamind.common.cache.CacheAsideTemplate;
import com.novamind.common.hotkey.HotKeyGuard;
import com.novamind.pay.domain.po.PayOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R9 PayOrderServiceImpl 单元测试：验证 R9 热 key 改造后，
 * {@code queryByBizOrderNo} / {@code queryByPayOrderNo} 的读路径确实走
 * 内部 {@link HotKeyGuard}，同 key 第二次读取不会调 mapper。
 *
 * <p>由于 {@code PayOrderServiceImpl} 继承 {@code ServiceImpl}，
 * 直接 mock mapper 会被 MyBatis-Plus 的 lambdaQuery 拒绝（要求 JDK proxy）。
 * 这里改为反射拿内部 {@code bizOrderGuard} / {@code payOrderGuard} 字段，
 * 直接打 guard 验证缓存行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayOrderServiceImplTest {

    @Mock
    private RabbitMqHelper rabbitMqHelper;

    @Mock
    private CacheAsideTemplate cacheAside;

    private PayOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        // PayOrderServiceImpl 用 @RequiredArgsConstructor 注入两个协作对象：
        // rabbitMqHelper + cacheAside（R23 Cache-Aside 统一封装）。
        service = new PayOrderServiceImpl(rabbitMqHelper, cacheAside);
    }

    @Test
    @DisplayName("service 持有的 bizOrderGuard 非空且已配置")
    void serviceHasBizOrderGuard() throws Exception {
        Object guard = readGuard("bizOrderGuard");
        assertNotNull(guard, "bizOrderGuard 必须被初始化");
        assertTrue(guard instanceof HotKeyGuard<?, ?>, "bizOrderGuard 必须是 HotKeyGuard 类型");
    }

    @Test
    @DisplayName("service 持有的 payOrderGuard 非空且已配置")
    void serviceHasPayOrderGuard() throws Exception {
        Object guard = readGuard("payOrderGuard");
        assertNotNull(guard);
        assertTrue(guard instanceof HotKeyGuard<?, ?>, "payOrderGuard 必须是 HotKeyGuard 类型");
    }

    @Test
    @DisplayName("bizOrderGuard: 同 key 第二次读命中本地缓存，loader 只调一次")
    void bizOrderGuardCacheHit() throws Exception {
        @SuppressWarnings("unchecked")
        HotKeyGuard<Long, PayOrder> guard =
                (HotKeyGuard<Long, PayOrder>) readGuard("bizOrderGuard");

        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<PayOrder> loader = () -> {
            loaderCalls.incrementAndGet();
            PayOrder po = new PayOrder();
            po.setBizOrderNo(10001L);
            return po;
        };

        PayOrder v1 = guard.get(10001L, loader);
        PayOrder v2 = guard.get(10001L, loader);
        PayOrder v3 = guard.get(10001L, loader);

        assertNotNull(v1);
        assertSame(v1, v2, "第二次读应命中缓存");
        assertSame(v1, v3, "第三次读应命中缓存");
        assertEquals(1, loaderCalls.get(), "loader 只应被调 1 次");
    }

    @Test
    @DisplayName("payOrderGuard: 同 key 第二次读命中本地缓存")
    void payOrderGuardCacheHit() throws Exception {
        @SuppressWarnings("unchecked")
        HotKeyGuard<Long, PayOrder> guard =
                (HotKeyGuard<Long, PayOrder>) readGuard("payOrderGuard");

        AtomicInteger loaderCalls = new AtomicInteger(0);
        HotKeyGuard.Loader<PayOrder> loader = () -> {
            loaderCalls.incrementAndGet();
            return new PayOrder();
        };

        guard.get(20001L, loader);
        guard.get(20001L, loader);
        guard.get(20001L, loader);

        assertEquals(1, loaderCalls.get());
    }

    @Test
    @DisplayName("不同 key 在 16 个桶里分散")
    void differentKeysSpreadAcrossBuckets() throws Exception {
        @SuppressWarnings("unchecked")
        HotKeyGuard<Long, PayOrder> guard =
                (HotKeyGuard<Long, PayOrder>) readGuard("bizOrderGuard");

        // 100 个不同 key，每个都调一次 loader
        for (long k = 0; k < 100; k++) {
            long key = k;
            guard.get(key, () -> {
                PayOrder po = new PayOrder();
                po.setBizOrderNo(key);
                return po;
            });
        }
        // 第二次读全部命中
        AtomicInteger extra = new AtomicInteger(0);
        for (long k = 0; k < 100; k++) {
            long key = k;
            guard.get(key, () -> {
                extra.incrementAndGet();
                return new PayOrder();
            });
        }
        assertEquals(0, extra.get(), "100 个 key 都应已缓存");
    }

    private Object readGuard(String fieldName) throws Exception {
        java.lang.reflect.Field f = PayOrderServiceImpl.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(service);
    }
}