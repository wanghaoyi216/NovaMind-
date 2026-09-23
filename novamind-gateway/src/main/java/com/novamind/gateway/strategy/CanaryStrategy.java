package com.novamind.gateway.strategy;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * R26 金丝雀发布策略
 *
 * <p><b>复用 R22 的 {@link com.novamind.gateway.gray.GrayReleaseService}</b>：
 * 金丝雀本质就是 R22 的渐进式灰度，故直接委托给 {@code GrayReleaseService}
 * 做分桶判定，本类只负责：</p>
 * <ol>
 *   <li>校验当前阶段属于合法的金丝雀阶梯（1% / 5% / 10% / 30% / 100%）</li>
 *   <li>提供阶梯升级 / 回滚 / 恢复的策略层封装</li>
 *   <li>记录金丝雀的"阶段+时间窗"用于发布治理（审计 / SLA 监控）</li>
 * </ol>
 *
 * <p><b>金丝雀阶梯</b>：</p>
 * <pre>
 *  1%  →  5%  →  10%  →  30%  →  100%
 *   │      │      │       │       │
 * 内部   种子   活跃   灰度    全量
 * 白名单 用户   用户   观察
 * </pre>
 *
 * <p><b>与蓝绿的根本区别</b>：</p>
 * <ul>
 *   <li>蓝绿只有 0% / 100% 两个状态；金丝雀可处于 5 档中的任意一档</li>
 *   <li>金丝雀依赖<b>一致性哈希分桶</b>保证同一用户多次请求走同一版本（粘性），
 *       蓝绿不需要（所有人同进同出）</li>
 *   <li>金丝雀升级是<b>单向阶梯</b>（1→5→10→30→100），不能跳级（除非紧急回滚后重新升）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanaryStrategy implements GrayStrategy<CanaryStrategy.CanaryConfig> {

    /** 阶梯百分比（与 R22 兼容：1% / 5% / 10% / 30% / 100%） */
    public static final int[] CANARY_LADDER = {1, 5, 10, 30, 100};

    /** 目标版本号 */
    public static final String TARGET_VERSION = "canary";

    /** 复用 R22 灰度服务（已含分桶、阶梯升级、回滚、恢复能力） */
    private final com.novamind.gateway.gray.GrayReleaseService grayService;

    /** 阶梯配置（与 R22 GrayRule 同构，但语义聚焦金丝雀） */
    private volatile CanaryConfig config = new CanaryConfig();

    @Override
    public GrayStrategyType getType() {
        return GrayStrategyType.CANARY;
    }

    @Override
    public String getTargetVersion() {
        return TARGET_VERSION;
    }

    @Override
    public CanaryConfig getConfig() {
        return config;
    }

    /**
     * 刷新配置（Nacos 推送时由 Router 调用）
     */
    public void refresh(CanaryConfig newConfig) {
        this.config = newConfig;
        log.info("[Canary] config refreshed, current phase = {}%", newConfig.getCurrentPercent());
    }

    @Override
    public boolean shouldRouteToGray(ServerHttpRequest request, Map<String, Object> context) {
        // 委托给 R22 GrayReleaseService 判定：
        //   1. enabled = false  → 直接 false
        //   2. userId 缺失      → false（未登录走 base）
        //   3. hash(userId) % 100 ∈ [0, percent-1] → true
        Object userIdObj = context == null ? null : context.get("userId");
        if (!(userIdObj instanceof Long) && !(userIdObj instanceof Integer)) {
            return false;
        }
        long userId = ((Number) userIdObj).longValue();
        int[] range = grayService.getCurrentBucketRange(config.getTargetAgent());
        if (range == null) {
            return false;
        }
        int bucket = (int) Math.floorMod(userId, 100);
        return bucket >= range[0] && bucket <= range[1];
    }

    @Override
    public void rollback() {
        // 金丝雀紧急回滚：复用 R22 的 rollback —— 把所有规则 disable、enabled=false
        grayService.rollback(config.getTargetAgent());
        log.warn("[Canary] EMERGENCY ROLLBACK via GrayReleaseService");
    }

    @Override
    public void resume() {
        grayService.resume();
        log.info("[Canary] resume, current phase = {}%", config.getCurrentPercent());
    }

    /**
     * 升到下一档（10 → 30 → 100；已是 100% 返回 false）
     */
    public boolean upgrade() {
        return grayService.upgradeToNextPhase(config.getTargetAgent());
    }

    /**
     * 校验给定的 percent 是否属于金丝雀合法阶梯
     */
    public static boolean isValidLadder(int percent) {
        for (int p : CANARY_LADDER) {
            if (p == percent) {
                return true;
            }
        }
        return false;
    }

    /**
     * 金丝雀配置（与 R22 兼容）
     */
    @Data
    public static class CanaryConfig {
        /** 目标 agent / 服务名 */
        private String targetAgent = "novamind-ai-agent";
        /** 当前阶段百分比（1 / 5 / 10 / 30 / 100） */
        private int currentPercent = 1;
        /** 灰度总开关 */
        private boolean enabled = true;
        /** 阶梯观察时长（分钟，每档至少观察该时长才允许升级） */
        private int observeMinutesPerStep = 15;
        /** 描述 */
        private String description = "金丝雀发布：渐进式阶梯放量";
    }
}
