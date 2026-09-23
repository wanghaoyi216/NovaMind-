package com.novamind.gateway.strategy;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Map;

/**
 * R26 灰度策略抽象接口
 *
 * <p>统一封装三种发布形态（蓝绿 / 金丝雀 / A/B）的判定逻辑，
 * 由 {@link GrayStrategyRouter} 根据 URL 路径或 header 选择具体实现。</p>
 *
 * <p><b>核心约定</b>：</p>
 * <ul>
 *   <li>{@link #getType()} 返回策略枚举，由 Router 用于二次校验</li>
 *   <li>{@link #shouldRouteToGray(ServerHttpRequest, Map)} 返回 true 表示该请求应走新版本，
 *       false 表示走 base（老版本）</li>
 *   <li>{@link #getTargetVersion()} 返回目标版本号（v2 / canary / blue 等），
 *       由 {@link com.novamind.gateway.gray.GrayReleaseFilter} 改写路径/透传 header</li>
 * </ul>
 *
 * <p><b>为什么用接口而不是 if/else 链</b>：
 * 三种策略的判定算法、状态机、配置完全不同；接口让每种策略独立演进、单元测试可独立运行。
 * 新增策略（如影子流量 / Traffic Mirroring）只需新增一个实现类 + Router 注册。</p>
 *
 * @param <C> 策略配置类型（蓝绿用 String、A/B 用 Map，金丝雀复用 R22 配置）
 */
public interface GrayStrategy<C> {

    /**
     * 返回策略类型
     */
    GrayStrategyType getType();

    /**
     * 返回目标版本号（用于改写 X-Gray-Version header / 路由表 version 段）
     */
    String getTargetVersion();

    /**
     * 返回策略当前配置快照（用于调试 / 监控 / 单元测试断言）
     */
    C getConfig();

    /**
     * 判定当前请求是否应走新版本
     *
     * @param request   当前 HTTP 请求（用于解析 userId / header / IP）
     * @param context   附加上下文（userId、region、deviceType、appVersion 等，由 Router 解析后传入）
     * @return true 走新版本；false 走 base 老版本
     */
    boolean shouldRouteToGray(ServerHttpRequest request, Map<String, Object> context);

    /**
     * 紧急回滚（蓝绿 / 金丝雀语义不同：蓝绿 = 切回 base；金丝雀 = 灰度比例 0%）
     */
    void rollback();

    /**
     * 恢复（回滚的反操作）
     */
    void resume();
}
