package com.novamind.gateway.strategy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R26 蓝绿发布策略
 *
 * <p><b>核心特征</b>：</p>
 * <ul>
 *   <li>两套<b>完全独立</b>的部署环境（blue = 老版本 / green = 新版本），共享 DB / Redis / MQ</li>
 *   <li>流量切换是<b>原子二选一</b>：要么 0%，要么 100%</li>
 *   <li>切换动作通过改 Nacos / LB 配置秒级生效；回滚同等速度</li>
 * </ul>
 *
 * <p><b>与金丝雀的关键区别</b>：</p>
 * <ul>
 *   <li>金丝雀同一时间可以多档比例共存（10% + 30% + 100%），蓝绿<b>只可能有一档生效</b></li>
 *   <li>金丝雀依赖一致性哈希保证粘性，蓝绿<b>不需要</b>粘性（所有人要么新要么旧）</li>
 *   <li>蓝绿<b>回滚成本极低</b>，金丝雀必须先回退到 0% 阶段</li>
 * </ul>
 *
 * <p><b>切换时延保证</b>：</p>
 * <ol>
 *   <li>Nacos 推送 {@code tj.gray.strategy.blue-green.active-version} 配置变更</li>
 *   <li>{@link com.novamind.gateway.gray.GrayReleaseProperties} 走 {@code @RefreshScope} 热更新</li>
 *   <li>本类内部 {@link AtomicReference} 原子更新 {@code activeVersion} 字段</li>
 *   <li>下一请求判定时直接读 atomic ref，<b>无锁无 IO，纳秒级</b></li>
 * </ol>
 *
 * <p>因此蓝绿切流量延迟 = Nacos 推送延迟（通常 &lt; 100ms）+ 内存原子切换（&lt; 1ms），
 * 端到端可在 <b>100ms 内</b>完成，符合题目要求。</p>
 */
@Slf4j
@Component
public class BlueGreenStrategy implements GrayStrategy<BlueGreenStrategy.BlueGreenConfig> {

    /** 新版本标识（路由表 / header 透传使用） */
    public static final String VERSION_GREEN = "green";
    /** 老版本标识 */
    public static final String VERSION_BLUE = "blue";

    /**
     * 当前生效版本（blue / green）。
     * 用 {@link AtomicReference} 保证多线程（Reactor event loop）下读写的可见性。
     */
    private final AtomicReference<String> activeVersion = new AtomicReference<>(VERSION_BLUE);

    /** 外部配置（@RefreshScope 注入） */
    private volatile BlueGreenConfig config = new BlueGreenConfig();

    @Override
    public GrayStrategyType getType() {
        return GrayStrategyType.BLUE_GREEN;
    }

    @Override
    public String getTargetVersion() {
        return activeVersion.get();
    }

    @Override
    public BlueGreenConfig getConfig() {
        return config;
    }

    /**
     * 刷新配置（Nacos 推送时由 Router 调用）
     */
    public void refresh(BlueGreenConfig newConfig) {
        this.config = newConfig;
        this.activeVersion.set(newConfig.getActiveVersion());
        log.info("[BlueGreen] switched active version → {}", newConfig.getActiveVersion());
    }

    @Override
    public boolean shouldRouteToGray(ServerHttpRequest request, Map<String, Object> context) {
        // 蓝绿不关心 userId / header —— 只看 activeVersion 是不是 green
        return VERSION_GREEN.equalsIgnoreCase(activeVersion.get());
    }

    @Override
    public void rollback() {
        // 紧急回滚：把 activeVersion 切回 blue（秒级）
        String previous = activeVersion.getAndSet(VERSION_BLUE);
        log.warn("[BlueGreen] EMERGENCY ROLLBACK: {} → blue", previous);
    }

    @Override
    public void resume() {
        // 恢复：按当前 config 重新激活（通常配合 Nacos 推送）
        activeVersion.set(config.getActiveVersion());
        log.info("[BlueGreen] resume, active version = {}", activeVersion.get());
    }

    /**
     * 蓝绿发布配置
     */
    @Data
    public static class BlueGreenConfig {
        /** 当前生效版本：blue / green */
        private String activeVersion = VERSION_BLUE;
        /** 灰度服务名（路由表 v2 实例组） */
        private String targetAgent = "novamind-ai-agent";
        /** 描述 */
        private String description = "蓝绿发布：两套环境互斥切换";
    }
}
