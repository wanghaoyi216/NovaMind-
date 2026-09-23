package com.novamind.gateway.gray;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 灰度发布核心服务
 *
 * <p>职责：</p>
 * <ul>
 *   <li>根据当前 phase 计算每个 agent 的灰度百分比（10% / 30% / 100%）</li>
 *   <li>提供阶段升级能力：{@link #upgradeToNextPhase(String)}</li>
 *   <li>提供紧急回滚能力：{@link #rollback(String)}</li>
 * </ul>
 *
 * <p><b>与 Nacos 的协作模式</b>：</p>
 * <pre>
 *  运维 / SRE 操作
 *    └─► Nacos 控制台编辑 gray-rule.yaml
 *          └─► 推送事件
 *                └─► @RefreshScope 重新绑定 GrayReleaseProperties
 *                      └─► 后续请求 GrayReleaseService#getCurrentGrayPercent 返回新值
 * </pre>
 *
 * <p><b>为什么不直接调用 Nacos OpenAPI 改配置</b>：
 * 灰度升级是高频操作（一天可能 3-5 次），通过 SRE 手工改 Nacos + 审批流程更安全；
 * 服务层只读配置 + 提供降级方法，避免误操作污染 Nacos 数据源。</p>
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class GrayReleaseService {

    /** 阶段升级顺序（与 gray-rule.yaml 中 rules[].phase 严格对应） */
    private static final int[] PHASE_LADDER = {10, 30, 100};

    private final GrayReleaseProperties properties;

    /**
     * 获取指定 agent 当前的灰度百分比
     *
     * @param agentName agent / 服务名（如 "novamind-ai-agent"）
     * @return 0 / 10 / 30 / 100；0 表示不灰度（走 base 版本）
     */
    public int getCurrentGrayPercent(String agentName) {
        if (!properties.isEnabled()) {
            return 0;
        }
        int current = properties.getCurrentPhase();
        for (GrayReleaseProperties.GrayRule rule : properties.getRules()) {
            if (rule.getPhase() == current
                    && rule.isEnabled()
                    && agentName.equals(rule.getAgent())) {
                return rule.getPercent();
            }
        }
        log.warn("[Gray] no active rule for agent={} currentPhase={}",
                agentName, current);
        return 0;
    }

    /**
     * 获取指定 agent 当前的命中桶区间
     *
     * <p>例如 10% 灰度时返回 [0, 9]，30% 返回 [0, 29]，100% 返回 [0, 99]。
     * 上层 {@link GrayReleaseFilter} 用该区间判断 userId 是否走新版本。</p>
     *
     * @param agentName agent / 服务名
     * @return [low, high] 闭区间，命中即走 gray 版本；返回 null 表示无灰度
     */
    public int[] getCurrentBucketRange(String agentName) {
        int percent = getCurrentGrayPercent(agentName);
        if (percent <= 0) {
            return null;
        }
        int modulus = properties.getBucketModulus();
        // 把 percent 转换为对应的桶索引 high（包含 high）
        // 10% → 0..9, 30% → 0..29, 100% → 0..99
        int high = (int) Math.ceil(percent / 100.0 * modulus) - 1;
        return new int[]{0, high};
    }

    /**
     * 升级到下一阶段（10 → 30 → 100；已是 100% 时返回 false）
     *
     * <p><b>本方法只改内存中的 properties</b>，不直接写 Nacos。
     * 调用方应负责把新值同步到 Nacos（通常由 SRE 走发布平台）。</p>
     *
     * @param agentName 仅升级该 agent；传 null 表示全局升级
     * @return true 升级成功，false 已达 100% 无需升级
     */
    public boolean upgradeToNextPhase(String agentName) {
        int current = properties.getCurrentPhase();
        int next = nextPhase(current);
        if (next == current) {
            log.info("[Gray] agent={} already at FULL (100%), skip upgrade", agentName);
            return false;
        }
        log.info("[Gray] upgrading agent={} from phase={} to phase={}",
                agentName, current, next);
        // 关闭当前 phase 规则，开启下一 phase 规则
        for (GrayReleaseProperties.GrayRule rule : properties.getRules()) {
            if (agentName == null || agentName.equals(rule.getAgent())) {
                if (rule.getPhase() == current) {
                    rule.setEnabled(false);
                }
                if (rule.getPhase() == next) {
                    rule.setEnabled(true);
                }
            }
        }
        properties.setCurrentPhase(next);
        return true;
    }

    /**
     * 紧急回滚：把灰度比例降到 0%（所有流量回 base 版本）
     *
     * <p>线上出现严重故障时调用，比 {@link #upgradeToNextPhase} 反向操作更直接。</p>
     */
    public void rollback(String agentName) {
        log.warn("[Gray] EMERGENCY ROLLBACK agent={} disable all rules", agentName);
        for (GrayReleaseProperties.GrayRule rule : properties.getRules()) {
            if (agentName == null || agentName.equals(rule.getAgent())) {
                rule.setEnabled(false);
            }
        }
        properties.setEnabled(false);
        properties.setCurrentPhase(0);
    }

    /**
     * 恢复灰度（回滚后的反操作，把 enabled 切回 true）
     */
    public void resume() {
        log.info("[Gray] resume gray release");
        properties.setEnabled(true);
        int current = properties.getCurrentPhase();
        for (GrayReleaseProperties.GrayRule rule : properties.getRules()) {
            if (rule.getPhase() == current) {
                rule.setEnabled(true);
            }
        }
    }

    /**
     * 内部辅助：根据当前阶段找下一阶段
     */
    private int nextPhase(int current) {
        for (int p : PHASE_LADDER) {
            if (p > current) {
                return p;
            }
        }
        return current;
    }

    /**
     * 调试用：返回所有 agent 的当前快照
     */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> snap = new HashMap<>();
        for (GrayReleaseProperties.GrayRule rule : properties.getRules()) {
            if (rule.isEnabled()) {
                snap.put(rule.getAgent(), rule.getPercent());
            }
        }
        return snap;
    }
}
