package com.novamind.gateway.strategy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R26 A/B 测试策略
 *
 * <p><b>核心特征</b>：</p>
 * <ul>
 *   <li>两个或多个版本<b>长期共存</b>（不收敛到 100%），用于对比核心业务指标</li>
 *   <li>按<b>业务规则</b>分桶：地域（region）、设备（deviceType）、客户端版本（appVersion）、
 *       用户分群（userGroup）等</li>
 *   <li>实验组与对照组流量<b>正交</b>，不交叉污染（同一用户始终进同一组）</li>
 * </ul>
 *
 * <p><b>与金丝雀的根本区别</b>：</p>
 * <ul>
 *   <li>金丝雀最终会全量；A/B 实验<b>长期共存</b>，可能 50%/50% 持续数月</li>
 *   <li>金丝雀按 userId 哈希分桶；A/B 按<b>业务维度</b>分桶（同一区域要么全 A 要么全 B）</li>
 *   <li>金丝雀只看"新版本是否安全"；A/B 看"<b>哪个版本效果更好</b>"（CTR、留存、GMV）</li>
 * </ul>
 *
 * <p><b>典型分桶规则</b>：</p>
 * <pre>
 *  if (region == "CN-North" && deviceType == "iOS" && appVersion >= "2.5.0")
 *      → 走 variant B（新功能）
 *  else
 *      → 走 variant A（对照组）
 * </pre>
 *
 * <p><b>同用户粘性</b>：与金丝雀一致，对同一 userId 持续返回相同结果，
 * 避免同一用户在实验中途被切到另一组导致指标污染。</p>
 */
@Slf4j
@Component
public class ABTestStrategy implements GrayStrategy<ABTestStrategy.ABTestConfig> {

    /** 对照组（A） */
    public static final String VARIANT_A = "variant-a";
    /** 实验组（B） */
    public static final String VARIANT_B = "variant-b";

    /**
     * 当前激活的实验 ID（用 AtomicReference 保证多线程可见性）
     */
    private final AtomicReference<String> activeExperimentId = new AtomicReference<>("default");

    /** 实验配置（@RefreshScope 注入） */
    private volatile ABTestConfig config = new ABTestConfig();

    @Override
    public GrayStrategyType getType() {
        return GrayStrategyType.AB_TEST;
    }

    @Override
    public String getTargetVersion() {
        return VARIANT_B;
    }

    @Override
    public ABTestConfig getConfig() {
        return config;
    }

    /**
     * 刷新配置（Nacos 推送时由 Router 调用）
     */
    public void refresh(ABTestConfig newConfig) {
        this.config = newConfig;
        this.activeExperimentId.set(newConfig.getExperimentId());
        log.info("[ABTest] experiment {} activated, traffic split = {}%",
                newConfig.getExperimentId(), newConfig.getExperimentPercent());
    }

    @Override
    public boolean shouldRouteToGray(ServerHttpRequest request, Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        ABTestConfig cfg = this.config;

        // ── 1. 规则前置过滤：只对满足分桶规则的请求做实验分组 ───────
        if (!matchesTargeting(context, cfg)) {
            return false; // 不在分桶规则内 → 走对照组 A
        }

        // ── 2. 实验分桶：用 userId 哈希保证粘性 ───────────────────
        Object userIdObj = context.get("userId");
        if (userIdObj == null) {
            return false;
        }
        long userId = ((Number) userIdObj).longValue();
        int bucket = (int) Math.floorMod(userId, 100);

        // experiment-percent 表示进入实验组（B）的比例，例如 50% → bucket ∈ [0, 49]
        return bucket < cfg.getExperimentPercent();
    }

    /**
     * 校验当前请求是否命中 A/B 实验分桶规则
     */
    private boolean matchesTargeting(Map<String, Object> context, ABTestConfig cfg) {
        if (cfg.getTargeting() == null || cfg.getTargeting().isEmpty()) {
            // 未配置规则 → 所有人参与
            return true;
        }
        // 遍历规则：所有非空规则都需命中（AND 语义）
        for (Map.Entry<String, String> e : cfg.getTargeting().entrySet()) {
            String dimension = e.getKey();
            String expected = e.getValue();
            Object actual = context.get(dimension);
            if (actual == null) {
                return false;
            }
            if (!Objects.equals(expected.toString().toLowerCase(),
                    actual.toString().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void rollback() {
        // A/B 实验回滚：直接把实验比例降到 0%（所有人回到对照组 A）
        log.warn("[ABTest] EMERGENCY ROLLBACK: experiment={} traffic → 0%",
                activeExperimentId.get());
        config.setExperimentPercent(0);
    }

    @Override
    public void resume() {
        // 恢复：重新读取 Nacos 中的配置（按需调用 refresh）
        log.info("[ABTest] resume, current config = {}", config);
    }

    /**
     * A/B 实验配置
     */
    @Data
    public static class ABTestConfig {
        /** 实验 ID */
        private String experimentId = "default";
        /** 实验组流量比例（0-100，例如 50 表示 50% 进 B 组） */
        private int experimentPercent = 50;
        /** 对照组版本标识 */
        private String variantA = VARIANT_A;
        /** 实验组版本标识 */
        private String variantB = VARIANT_B;
        /** 分桶规则（AND 关系）。key 支持 region / deviceType / appVersion / userGroup 等 */
        private Map<String, String> targeting;
        /** 实验开始时间（ISO 8601） */
        private String startTime;
        /** 实验结束时间 */
        private String endTime;
        /** 目标 agent / 服务名 */
        private String targetAgent = "novamind-ai-agent";
        /** 描述 */
        private String description = "A/B 实验：按业务规则长期分桶对比";
    }
}
