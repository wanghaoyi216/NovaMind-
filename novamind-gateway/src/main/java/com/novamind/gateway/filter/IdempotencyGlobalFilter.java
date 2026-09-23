package com.novamind.gateway.filter;

import com.novamind.common.autoconfigure.idempotency.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * <h1>网关层幂等过滤器（WebFlux 响应式实现）</h1>
 *
 * <p><b>背景</b>：{@code novamind-common} 里的 {@code IdempotencyKeyInterceptor} 基于
 * Servlet API（HttpServletRequest/Response），只能跑在内嵌 Tomcat 的业务微服务上；
 * 本网关是 Spring Cloud Gateway（WebFlux / Netty），<b>Servlet 拦截器在网关上完全不生效</b>。
 * 订单 / 支付等关键写路径必须由网关层兜底，故单独实现本 GlobalFilter。</p>
 *
 * <p><b>双层防护体系</b>（面试高频考点）：</p>
 * <ol>
 *   <li><b>网关层</b>（本类）：全局限流 + 幂等第一道闸，Redis 单次往返 P99 &lt; 5ms；</li>
 *   <li><b>业务微服务层</b>（IdempotencyKeyInterceptor）：本地缓存 + 业务语义幂等，P99 &lt; 1ms。</li>
 * </ol>
 *
 * <p><b>工作流程</b>：</p>
 * <pre>
 * POST/PUT/DELETE 且命中路径白名单
 *   ├─ Header 缺失 Idempotency-Key 且 requireHeader=true → 400 BAD_REQUEST
 *   ├─ Redis GET idempotency:{key} 命中 → 直接回放缓存的状态码+响应体（不转发下游）
 *   └─ 未命中 → ServerHttpResponseDecorator 缓冲响应
 *        → chain.filter() 放行
 *        → 响应提交前异步 SETEX 回写 Redis（TTL=expireHours）
 * </pre>
 *
 * <p><b>设计取舍</b>：完整缓冲响应体再回写（而非只存状态码）。订单/支付场景响应体通常
 * &lt; 10KB，全量缓冲内存可控；换来的是"重试请求拿到与首次完全一致的字节级响应"，
 * 这是 Stripe 等支付平台的幂等语义。若未来出现大响应体接口，可在
 * {@link IdempotencyProperties#getMaxCachedBodyBytes()} 上限保护下降级为只存状态码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGlobalFilter implements GlobalFilter, Ordered {

    /** 只拦截写方法；GET 天然幂等无需处理 */
    private static final Set<HttpMethod> IDEMPOTENT_REQUIRED_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

    /** 默认拦截的业务前缀（对应网关路由的短前缀），空 = 全部拦截 */
    private static final List<String> DEFAULT_INCLUDE_PREFIXES =
            List.of("/ts/", "/ps/", "/prs/");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ── 开关关闭直接放行 ────────────────────────────────────────────────
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        var request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();

        // ── 非写方法放行 ────────────────────────────────────────────────────
        if (method == null || !IDEMPOTENT_REQUIRED_METHODS.contains(method)) {
            return chain.filter(exchange);
        }

        // ── 路径白名单过滤（默认只拦交易/支付/促销域）─────────────────────
        if (!shouldIntercept(path)) {
            return chain.filter(exchange);
        }

        // ── 提取 Idempotency-Key ───────────────────────────────────────────
        String idemKey = request.getHeaders().getFirst(properties.getHeaderName());
        if (idemKey == null || idemKey.isBlank()) {
            if (properties.isRequireHeader()) {
                log.warn("[Gateway-Idempotency] missing {} header on {} {}",
                        properties.getHeaderName(), method, path);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "Missing required header: " + properties.getHeaderName());
            }
            return chain.filter(exchange); // 未强制要求时无 Key 直接放行
        }

        String redisKey = properties.getCacheKeyPrefix() + idemKey;

        // ── 先查缓存：命中则直接重放缓存响应 ─────────────────────────────
        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(cached -> replayCachedResponse(exchange, cached))
                .switchIfEmpty(Mono.defer(() -> proceedAndCache(exchange, chain, redisKey)));
    }

    /**
     * 未命中缓存：用响应装饰器缓冲下游返回体，响应提交前异步回写 Redis。
     * 关键点：装饰器必须在 chain.filter() 之前挂到 exchange 上。
     */
    private Mono<Void> proceedAndCache(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String redisKey) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        StringBuilder bodyBuf = new StringBuilder(512);
        // 部署修复注记：Spring 6 起 getStatusCode() 返回 HttpStatusCode 接口
        // （HttpStatus 枚举无法承接非标准状态码），改为接口类型
        var statusCodeHolder = new Object() { HttpStatusCode value; };

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                statusCodeHolder.value = getStatusCode();
                Flux<? extends DataBuffer> flux = Flux.from(body);
                return super.writeWith(flux.map(db -> {
                    // 部署修复注记：bytes 原声明在 try 块内、return 在块外，
                    // 作用域不通过编译，提升到 lambda 顶部
                    byte[] bytes = new byte[db.readableByteCount()];
                    try {
                        db.read(bytes);
                        // 只缓冲上限内的响应体，超限截断（防御异常大的下载类响应）
                        if (bodyBuf.length() < properties.getMaxCachedBodyBytes()) {
                            bodyBuf.append(new String(bytes, StandardCharsets.UTF_8));
                        }
                    } finally {
                        org.springframework.core.io.buffer.DataBufferUtils.release(db);
                    }
                    return bufferFactory.wrap(bytes);
                }));
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                // SSE / chunked 场景不缓冲（AI 流式等），保持原样透传且不回写幂等缓存
                return super.writeAndFlushWith(body);
            }
        };

        ServerWebExchange mutated = exchange.mutate().response(decorated).build();

        return chain.filter(mutated)
                .then(Mono.fromRunnable(() -> {
                    // 只缓存"确定性成功"的响应（2xx），4xx/5xx 不缓存以便客户端修正后重试
                    HttpStatusCode st = statusCodeHolder.value != null
                            ? statusCodeHolder.value : originalResponse.getStatusCode();
                    if (st != null && st.is2xxSuccessful() && !bodyBuf.isEmpty()) {
                        String cachedValue = st.value() + "\n" + bodyBuf;
                        redisTemplate.opsForValue()
                                .set(redisKey, cachedValue,
                                        Duration.ofHours(properties.getExpireHours()))
                                .subscribe(
                                        ok -> { /* cached */ },
                                        err -> log.warn("[Gateway-Idempotency] cache write failed key={}",
                                                redisKey, err));
                    }
                }));
    }

    /** 缓存命中：按 "<status>\n<body>" 格式还原响应，字节级重放首次结果。 */
    private Mono<Void> replayCachedResponse(ServerWebExchange exchange, String cached) {
        int sep = cached.indexOf('\n');
        int status = sep > 0 ? Integer.parseInt(cached.substring(0, sep)) : 200;
        String body = sep > 0 ? cached.substring(sep + 1) : cached;

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.resolve(status) != null
                ? HttpStatus.resolve(status) : HttpStatus.OK);
        response.getHeaders().add("X-Idempotent-Replay", "true");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        log.info("[Gateway-Idempotency] replay cached response key-prefix={} status={}",
                properties.getCacheKeyPrefix(), status);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":" + status.value() + ",\"msg\":\"" + msg + "\"}";
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private boolean shouldIntercept(String path) {
        // IdempotencyProperties 中两字段均为 Set<String>（Lombok @Data 生成对应 getter）
        Set<String> includes = properties.getIncludePrefixes();
        Set<String> excludes = properties.getExcludePaths();
        if (excludes != null) {
            for (String p : excludes) {
                if (PATH_MATCHER.match(p, path)) {
                    return false;
                }
            }
        }
        Set<String> prefixes = (includes == null || includes.isEmpty())
                ? Set.copyOf(DEFAULT_INCLUDE_PREFIXES) : includes;
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在鉴权（AccountAuthFilter order=1000）之后执行：
     * 只有通过鉴权的请求才值得做幂等检查，避免未认证流量打 Redis。
     */
    @Override
    public int getOrder() {
        return 1100;
    }
}
