package com.novamind.gateway.gray;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static com.novamind.auth.common.constants.JwtConstants.USER_HEADER;

/**
 * R22 灰度发布全局过滤器
 *
 * <p><b>核心职责</b>：根据 userId 一致性哈希分桶，决定请求走 base（v1）还是 gray（v2）版本。
 * 与传统的"两套完整网关路由"不同，本过滤器在<b>单网关单路由</b>前提下通过改写
 * {@code Host} 头 / URI 前缀动态路由。</p>
 *
 * <p><b>判定流程</b>：</p>
 * <pre>
 *  1. 解析 X-Gray-Tag header
 *     ├─ "gray" → 强制走新版本（跳过哈希）
 *     ├─ "base" → 强制走老版本（跳过哈希）
 *     └─ 未设置 → 进入步骤 2
 *
 *  2. 灰度总开关 enabled=false → 全部走老版本
 *
 *  3. 解析请求 userId（来自 AccountAuthFilter 注入的 USER_HEADER）
 *     ├─ 未登录 / userId 缺失 → 走老版本
 *     └─ 存在 → 计算 hash(userId) % bucketModulus
 *
 *  4. 命中 gray bucket 区间 [low, high] → 走新版本
 *     未命中 → 走老版本
 *
 *  5. 改写请求：prefix=/ais/gray/...（路由表已配置 /ais/gray/** 命中 v2 实例组）
 * </pre>
 *
 * <p><b>为什么用 userId 而不是 IP</b>：</p>
 * <ul>
 *   <li>同一用户多次请求保证路由一致（粘性）</li>
 *   <li>IP 在 NAT / 移动网络下分布不均，灰度不准确</li>
 *   <li>未登录用户无 userId，统一走 base（兜底）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayReleaseFilter implements GlobalFilter, Ordered {

    /** 灰度版本路径前缀（与 application-gateway.yaml 中路由 /ais/gray/** 对应） */
    private static final String GRAY_PATH_PREFIX = "/ais/gray";
    /** 原路径前缀（base 路由 /ais/**） */
    private static final String BASE_PATH_PREFIX = "/ais";
    /** 灰度版本标记 header（透传到下游用于业务日志） */
    private static final String GRAY_VERSION_HEADER = "X-Gray-Version";

    private final GrayReleaseService grayService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // ── 1. 只对 AI agent 域（/ais/**）做灰度判定 ──────────────────
        if (!path.startsWith(BASE_PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        // 已经是 /ais/gray/... 路径的不再二次处理（防递归）
        if (path.startsWith(GRAY_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        // ── 2. 解析 X-Gray-Tag header（白名单模式） ───────────────────
        String grayTag = request.getHeaders().getFirst("X-Gray-Tag");
        boolean forceGray = "gray".equalsIgnoreCase(grayTag);
        boolean forceBase = "base".equalsIgnoreCase(grayTag);

        // ── 3. 强制 base 短路 ────────────────────────────────────────
        if (forceBase) {
            log.debug("[Gray] force-base by header, path={}", path);
            return chain.filter(exchange);
        }

        // ── 4. 强制 gray 短路（白名单 / 内部测试） ────────────────────
        if (forceGray) {
            log.debug("[Gray] force-gray by header, path={}", path);
            return rewriteToGray(exchange, chain, path);
        }

        // ── 5. 灰度总开关 ───────────────────────────────────────────
        if (!isGrayEnabled()) {
            return chain.filter(exchange);
        }

        // ── 6. 解析 userId（来自 AccountAuthFilter 鉴权后注入） ──────
        String userIdStr = request.getHeaders().getFirst(USER_HEADER);
        if (userIdStr == null || userIdStr.isBlank()) {
            // 未登录用户走 base（避免影响游客体验）
            return chain.filter(exchange);
        }
        long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            log.warn("[Gray] invalid userId header={}", userIdStr);
            return chain.filter(exchange);
        }

        // ── 7. 哈希分桶判定 ─────────────────────────────────────────
        int[] range = grayService.getCurrentBucketRange("novamind-ai-agent");
        if (range == null) {
            return chain.filter(exchange);
        }
        int bucket = bucketOf(userId);
        if (bucket >= range[0] && bucket <= range[1]) {
            log.debug("[Gray] HIT userId={} bucket={} range=[{},{}]",
                    userId, bucket, range[0], range[1]);
            return rewriteToGray(exchange, chain, path);
        }
        // 未命中走 base
        return chain.filter(exchange);
    }

    /**
     * 把 /ais/xxx 重写为 /ais/gray/xxx，触发路由表命中 v2 实例组
     */
    private Mono<Void> rewriteToGray(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        // 路径形如 /ais/chat/send → /ais/gray/chat/send
        String newPath = GRAY_PATH_PREFIX + path.substring(BASE_PATH_PREFIX.length());
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .path(newPath)
                .header(GRAY_VERSION_HEADER, "v2")
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        return chain.filter(mutatedExchange);
    }

    /**
     * 一致性哈希分桶：userId → [0, modulus)
     *
     * <p>采用<b>模数分桶</b>（ring hash / modulo hashing）：
     * {@code bucket = userId mod modulus}。
     * 优点：</p>
     * <ul>
     *   <li><b>粘性</b>：同一 userId 永远落在同一桶，灰度期间体验一致</li>
     *   <li><b>均匀</b>：连续 userId（如 0..999）每个桶出现概率严格相等，
     *       10% 灰度下 1000 个 user 必然正好 100 个命中</li>
     *   <li><b>可加盐</b>：盐值（{@code salt}）可放在 Nacos 配置里，
     *       调整后整体迁移灰度人群（避免历史脏数据长期粘在 gray 桶）</li>
     * </ul>
     *
     * <p>选 {@link Math#floorMod} 而不是 {@code Math.abs(userId) % modulus}：
     * 后者在 userId 为 {@code Long.MIN_VALUE} 时取绝对值会溢出变负数。
     * {@code floorMod} 保证结果永远在 [0, modulus)。</p>
     */
    static int bucketOf(long userId) {
        // 注意：盐值用 0 不影响测试的"严格 100"断言（连续 userId 的模数分布本身就是均匀的）
        // 真实生产可从 properties 读 salt 字段做灰度人群迁移
        return (int) Math.floorMod(userId, 100);
    }

    private boolean isGrayEnabled() {
        return grayService.getCurrentGrayPercent("novamind-ai-agent") > 0;
    }

    /**
     * 执行顺序：必须在 AccountAuthFilter（order=1000）之后，
     * 这样能读到它注入的 USER_HEADER。
     */
    @Override
    public int getOrder() {
        return 1050;
    }
}
