package com.novamind.gateway.sentinel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * <h1>R9 Hot Key 防护：网关层全局热点过滤</h1>
 *
 * <p>拦截 {@code /pay/**}、{@code /orders/**} 这类带热参数的请求，
 * 提取 {@code bizOrderNo} / {@code couponId} 走 {@link HotKeyDispatcher}。
 * 被 Sentinel 限流（{@code passHotKey == false}）时直接返回 429，不再向下游微服务转发。</p>
 *
 * @author R9-refactor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeyGlobalFilter implements GlobalFilter, Ordered {

    /** 受热点限流保护的路径前缀 */
    private static final Set<String> PROTECTED_PATH_PREFIXES = Set.of(
            "/pay/", "/orders/", "/trade/"
    );

    private final HotKeyDispatcher dispatcher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!isProtected(path)) {
            return chain.filter(exchange);
        }
        Map<String, String> params = extractParams(exchange);
        try {
            boolean passed = dispatcher.passHotKeyFromParams(params);
            if (!passed) {
                return reject(exchange);
            }
        } catch (Exception e) {
            log.warn("[HotKey] 网关异常, 放行, path={}", path, e);
        }
        return chain.filter(exchange);
    }

    private boolean isProtected(String path) {
        for (String prefix : PROTECTED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private Map<String, String> extractParams(ServerWebExchange exchange) {
        Map<String, String> map = new HashMap<>(2);
        MultiValueMap<String, String> query = exchange.getRequest().getQueryParams();
        String bizOrderNo = query.getFirst("bizOrderNo");
        String couponId = query.getFirst("couponId");
        if (bizOrderNo != null) map.put("bizOrderNo", bizOrderNo);
        if (couponId != null) map.put("couponId", couponId);
        return map;
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBufferFactory bf = response.bufferFactory();
        byte[] body = ("{\"code\":429,\"msg\":\"hot key limited\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = bf.allocateBuffer(body.length);
        buffer.write(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 跑在 idempotency filter 之后，留给 Idempotency-Key 优先处理
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}