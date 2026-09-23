package com.novamind.gateway.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R26 灰度策略路由器
 *
 * <p><b>核心职责</b>：</p>
 * <ol>
 *   <li>根据 <b>URL 路径</b> 或 <b>Header</b> 选择具体的 {@link GrayStrategy} 实现</li>
 *   <li>解析请求上下文（userId、region、deviceType、appVersion）传入策略判定</li>
 *   <li>维护路由表（path-prefix / header-name → strategy-type），支持 Nacos 热更新</li>
 * </ol>
 *
 * <p><b>选择优先级</b>（从高到低）：</p>
 * <pre>
 *  1. Header 强制：X-Gray-Strategy=BLUE_GREEN|CANARY|AB_TEST  → 强制使用该策略
 *  2. URL 路径前缀：/ais/blue-green/**  → 蓝绿
 *                  /ais/canary/**     → 金丝雀
 *                  /ais/ab-test/**    → A/B
 *  3. 默认策略（global.default-strategy）
 * </pre>
 *
 * <p><b>与 {@link com.novamind.gateway.gray.GrayReleaseFilter} 的协作</b>：</p>
 * <pre>
 *  GrayReleaseFilter（order=1050）
 *      └─► GrayStrategyRouter.route(request, context)
 *            └─► 选中的 GrayStrategy.shouldRouteToGray(request, context)
 *                  └─► true  → 改写路径为 /ais/&lt;strategy-target&gt;/...
 *                  └─► false → 保持原路径
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayStrategyRouter implements InitializingBean {

    /** Header 名：用于强制指定策略类型（绕过 URL 匹配） */
    public static final String STRATEGY_HEADER = "X-Gray-Strategy";

    /** 三个策略实现（Spring 自动注入） */
    private final BlueGreenStrategy blueGreenStrategy;
    private final CanaryStrategy canaryStrategy;
    private final ABTestStrategy abTestStrategy;

    /** 策略注册表（GrayStrategyType → 实例） */
    private final Map<GrayStrategyType, GrayStrategy<?>> registry = new ConcurrentHashMap<>();

    /** 路由表：path-prefix → strategy-type（按匹配长度降序，最长前缀优先） */
    private volatile List<PathRoute> pathRoutes = new ArrayList<>();

    /** Header 路由表：header-name → strategy-type */
    private final Map<String, GrayStrategyType> headerRoutes = new ConcurrentHashMap<>();

    /** 默认策略 */
    private volatile GrayStrategyType defaultStrategy = GrayStrategyType.CANARY;

    @Override
    public void afterPropertiesSet() {
        // 注册三种策略
        registry.put(GrayStrategyType.BLUE_GREEN, blueGreenStrategy);
        registry.put(GrayStrategyType.CANARY, canaryStrategy);
        registry.put(GrayStrategyType.AB_TEST, abTestStrategy);

        // 初始化默认路由表（与 nacos_config_export/gray-strategy.yaml 保持一致）
        rebuildPathRoutes();
        headerRoutes.put(STRATEGY_HEADER.toLowerCase(), GrayStrategyType.CANARY);
        log.info("[StrategyRouter] initialized with strategies={}, default={}",
                registry.keySet(), defaultStrategy);
    }

    /**
     * 核心方法：根据请求选择策略
     *
     * @param request 当前 HTTP 请求
     * @param context 上下文（userId / region / deviceType / appVersion）
     * @return 选中的策略实例；若默认策略未注册返回 null
     */
    public GrayStrategy<?> route(ServerHttpRequest request, Map<String, Object> context) {
        // ── 1. Header 强制指定 ─────────────────────────────────────
        GrayStrategyType type = resolveByHeader(request);
        if (type != null) {
            return lookup(type);
        }

        // ── 2. URL 路径前缀匹配（最长前缀优先） ─────────────────────
        type = resolveByPath(request);
        if (type != null) {
            return lookup(type);
        }

        // ── 3. 默认策略 ───────────────────────────────────────────
        return lookup(defaultStrategy);
    }

    /**
     * 只做类型解析，不查 registry（用于 Filter 中日志/统计）
     */
    public GrayStrategyType resolveType(ServerHttpRequest request) {
        GrayStrategyType type = resolveByHeader(request);
        if (type != null) {
            return type;
        }
        type = resolveByPath(request);
        if (type != null) {
            return type;
        }
        return defaultStrategy;
    }

    private GrayStrategyType resolveByHeader(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(STRATEGY_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            GrayStrategyType type = GrayStrategyType.valueOf(header.toUpperCase());
            log.debug("[StrategyRouter] header override: {} → {}", STRATEGY_HEADER, type);
            return type;
        } catch (IllegalArgumentException e) {
            log.warn("[StrategyRouter] unknown strategy header value: {}", header);
            return null;
        }
    }

    private GrayStrategyType resolveByPath(ServerHttpRequest request) {
        String path = request.getPath().value();
        for (PathRoute route : pathRoutes) {
            if (path.startsWith(route.prefix)) {
                return route.type;
            }
        }
        return null;
    }

    private GrayStrategy<?> lookup(GrayStrategyType type) {
        if (type == null) {
            return null;
        }
        return registry.get(type);
    }

    /**
     * 重建路径路由表（按前缀长度降序）
     */
    public void rebuildPathRoutes() {
        List<PathRoute> routes = new ArrayList<>();
        routes.add(new PathRoute("/ais/blue-green/", GrayStrategyType.BLUE_GREEN));
        routes.add(new PathRoute("/ais/canary/", GrayStrategyType.CANARY));
        routes.add(new PathRoute("/ais/ab-test/", GrayStrategyType.AB_TEST));
        // 按 prefix.length() 降序 → 最长前缀优先匹配
        routes.sort((a, b) -> Integer.compare(b.prefix.length(), a.prefix.length()));
        this.pathRoutes = routes;
    }

    /**
     * 注册自定义路径路由
     */
    public void addPathRoute(String prefix, GrayStrategyType type) {
        List<PathRoute> routes = new ArrayList<>(this.pathRoutes);
        routes.add(new PathRoute(prefix, type));
        routes.sort((a, b) -> Integer.compare(b.prefix.length(), a.prefix.length()));
        this.pathRoutes = routes;
    }

    /**
     * 设置默认策略
     */
    public void setDefaultStrategy(GrayStrategyType type) {
        this.defaultStrategy = type;
        log.info("[StrategyRouter] default strategy → {}", type);
    }

    /**
     * 解析标准 context：userId / region / deviceType / appVersion
     */
    public Map<String, Object> resolveContext(ServerHttpRequest request, Long userId) {
        Map<String, Object> ctx = new HashMap<>();
        if (userId != null) {
            ctx.put("userId", userId);
        }
        // 从常见 Header 中提取分桶维度
        putIfPresent(ctx, "region", request.getHeaders().getFirst("X-Region"));
        putIfPresent(ctx, "deviceType", request.getHeaders().getFirst("X-Device-Type"));
        putIfPresent(ctx, "appVersion", request.getHeaders().getFirst("X-App-Version"));
        putIfPresent(ctx, "userGroup", request.getHeaders().getFirst("X-User-Group"));
        putIfPresent(ctx, "clientIp", request.getRemoteAddress() == null
                ? null : request.getRemoteAddress().getAddress().getHostAddress());
        return ctx;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * 路径路由条目
     */
    private record PathRoute(String prefix, GrayStrategyType type) {
    }
}
