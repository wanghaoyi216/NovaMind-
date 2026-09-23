package com.novamind.gateway.gray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R22 灰度发布过滤器单元测试
 *
 * <p><b>核心验证目标</b>：</p>
 * <ul>
 *   <li>1000 个连续 userId（0..999）在 10% 灰度阶段<b>正好 100 个</b>走新版本</li>
 *   <li>30% 阶段正好 300 个，100% 阶段 1000 个全走新版本</li>
 *   <li>灰度关闭（enabled=false）时所有请求回 base</li>
 *   <li>阶段升级方法（10→30→100）正确切换规则</li>
 * </ul>
 *
 * <p><b>为什么 1000 个连续 userId 能"正好 100"？</b>
 * 因为 {@link GrayReleaseFilter#bucketOf(long)} 用 {@code userId mod 100} 做分桶，
 * 连续输入 0..999 恰好每个 bucket 出现 10 次；10% 灰度命中 [0,9] 这 10 个 bucket，
 * 故正好 10×10 = 100 个 user 命中。这就是"模数哈希"比"伪随机哈希"在灰度场景的优势——
 * 可预测、可复现、易于在测试中精确断言。</p>
 */
class GrayReleaseFilterTest {

    private GrayReleaseProperties properties;
    private GrayReleaseService service;
    private GrayReleaseFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GrayReleaseProperties();
        // 默认配置：10% 灰度已启用
        properties.setEnabled(true);
        properties.setCurrentPhase(10);
        properties.setBucketModulus(100);

        List<GrayReleaseProperties.GrayRule> rules = new ArrayList<>();
        rules.add(rule(10, 10, true));
        rules.add(rule(30, 30, false));
        rules.add(rule(100, 100, false));
        properties.setRules(rules);

        service = new GrayReleaseService(properties);
        filter = new GrayReleaseFilter(service);
    }

    private GrayReleaseProperties.GrayRule rule(int phase, int percent, boolean enabled) {
        GrayReleaseProperties.GrayRule r = new GrayReleaseProperties.GrayRule();
        r.setPhase(phase);
        r.setPercent(percent);
        r.setEnabled(enabled);
        r.setAgent("novamind-ai-agent");
        r.setVersion("v2");
        return r;
    }

    // ────────────────────────────────────────────────────────────────────
    // 1. 核心断言：1000 个 userId 在 10% 阶段正好 100 命中
    // ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10% 阶段：1000 个连续 userId 正好 100 个走 gray")
    void tenPercentHitsExactly100Of1000() {
        int grayCount = simulateGray(0L, 1000L);

        assertEquals(100, grayCount,
                "10% 阶段期望 1000 个 user 中正好 100 命中 gray，实际 " + grayCount);
    }

    @Test
    @DisplayName("30% 阶段：1000 个连续 userId 正好 300 个走 gray")
    void thirtyPercentHitsExactly300Of1000() {
        service.upgradeToNextPhase("novamind-ai-agent"); // 10 → 30
        int grayCount = simulateGray(0L, 1000L);

        assertEquals(300, grayCount,
                "30% 阶段期望 1000 个 user 中正好 300 命中 gray，实际 " + grayCount);
    }

    @Test
    @DisplayName("100% 阶段：所有 user 都走 gray")
    void fullReleaseHitsAll() {
        service.upgradeToNextPhase("novamind-ai-agent"); // 10 → 30
        service.upgradeToNextPhase("novamind-ai-agent"); // 30 → 100
        int grayCount = simulateGray(0L, 1000L);

        assertEquals(1000, grayCount, "100% 阶段应全量命中");
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. Service 层：阶段升级 / 回滚
    // ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getCurrentGrayPercent 返回当前阶段百分比")
    void getCurrentGrayPercentReturnsActive() {
        assertEquals(10, service.getCurrentGrayPercent("novamind-ai-agent"));
        service.upgradeToNextPhase("novamind-ai-agent");
        assertEquals(30, service.getCurrentGrayPercent("novamind-ai-agent"));
        service.upgradeToNextPhase("novamind-ai-agent");
        assertEquals(100, service.getCurrentGrayPercent("novamind-ai-agent"));
    }

    @Test
    @DisplayName("已到 100% 时再升级返回 false")
    void upgradeAtFullReturnsFalse() {
        service.upgradeToNextPhase("novamind-ai-agent"); // 10 → 30
        service.upgradeToNextPhase("novamind-ai-agent"); // 30 → 100
        boolean upgraded = service.upgradeToNextPhase("novamind-ai-agent"); // 已是 100%
        assertEquals(false, upgraded);
    }

    @Test
    @DisplayName("回滚后灰度关闭，所有 user 走 base")
    void rollbackDisablesAllRules() {
        service.rollback("novamind-ai-agent");
        int grayCount = simulateGray(0L, 1000L);
        assertEquals(0, grayCount, "回滚后应 0 命中");
        assertEquals(0, service.getCurrentGrayPercent("novamind-ai-agent"));
    }

    @Test
    @DisplayName("灰度总开关关闭时无命中")
    void disabledByConfigHitsNone() {
        properties.setEnabled(false);
        int grayCount = simulateGray(0L, 1000L);
        assertEquals(0, grayCount, "enabled=false 时应 0 命中");
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. Filter 层：bucketOf 一致性哈希分桶
    // ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("bucketOf：连续 userId 0..999 的 bucket 分布严格均匀（每桶 10 次）")
    void bucketDistributionIsUniform() {
        int[] count = new int[100];
        for (long i = 0; i < 1000; i++) {
            int b = GrayReleaseFilter.bucketOf(i);
            count[b]++;
            assertTrue(b >= 0 && b < 100, "bucket 越界: " + b);
        }
        for (int i = 0; i < 100; i++) {
            assertEquals(10, count[i],
                    "bucket " + i + " 应出现 10 次，实际 " + count[i]);
        }
    }

    @Test
    @DisplayName("bucketOf：粘性（同 userId 永远落同 bucket）")
    void bucketIsSticky() {
        long userId = 1234567L;
        int first = GrayReleaseFilter.bucketOf(userId);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, GrayReleaseFilter.bucketOf(userId),
                    "同 userId 多次调用应返回同 bucket");
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. Service 层：bucket-range 区间
    // ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10% 阶段 bucket-range = [0, 9]")
    void tenPercentBucketRange() {
        int[] range = service.getCurrentBucketRange("novamind-ai-agent");
        assertNotNull(range);
        assertEquals(0, range[0]);
        assertEquals(9, range[1]);
    }

    @Test
    @DisplayName("100% 阶段 bucket-range = [0, 99]")
    void fullReleaseBucketRange() {
        service.upgradeToNextPhase("novamind-ai-agent");
        service.upgradeToNextPhase("novamind-ai-agent");
        int[] range = service.getCurrentBucketRange("novamind-ai-agent");
        assertNotNull(range);
        assertEquals(0, range[0]);
        assertEquals(99, range[1]);
    }

    @Test
    @DisplayName("灰度关闭时 bucket-range = null")
    void disabledReturnsNullRange() {
        properties.setEnabled(false);
        assertNull(service.getCurrentBucketRange("novamind-ai-agent"));
    }

    // ────────────────────────────────────────────────────────────────────
    // 5. Snapshot：调试视图
    // ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("snapshot 只展示当前激活的 agent 灰度比例")
    void snapshotShowsOnlyActiveRules() {
        var snap = service.snapshot();
        assertEquals(1, snap.size());
        assertEquals(10, snap.get("novamind-ai-agent"));

        service.upgradeToNextPhase("novamind-ai-agent");
        snap = service.snapshot();
        assertEquals(30, snap.get("novamind-ai-agent"));
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部辅助：模拟 N 个 userId 跑一遍灰度判定，返回命中 gray 的数量
    // 这里直接复用 Filter 的 bucketOf + Service 的 bucket-range 判定逻辑
    // ────────────────────────────────────────────────────────────────────
    private int simulateGray(long from, long to) {
        int[] range = service.getCurrentBucketRange("novamind-ai-agent");
        if (range == null) {
            return 0;
        }
        int hit = 0;
        for (long uid = from; uid < to; uid++) {
            int bucket = GrayReleaseFilter.bucketOf(uid);
            if (bucket >= range[0] && bucket <= range[1]) {
                hit++;
            }
        }
        return hit;
    }
}
