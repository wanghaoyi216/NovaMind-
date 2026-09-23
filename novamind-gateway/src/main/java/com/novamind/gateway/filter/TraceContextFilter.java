package com.novamind.gateway.filter;

import cn.hutool.core.lang.UUID;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * <h2>网关层 Trace 上下文过滤器 (R13 OpenTelemetry)</h2>
 *
 * <p><b>职责</b>（两条腿走路）：</p>
 * <ol>
 *   <li><b>写入 MDC</b>：把当前请求的 traceId 放进 SLF4J MDC（key= {@code traceId}），
 *       让网关侧的日志格式 {@code %X{traceId}} 能输出可关联的 ID；</li>
 *   <li><b>透传下游</b>：把 traceId 同时塞进 HTTP 响应头 {@code X-Trace-Id}（便于排查时
 *       客户端也能拿到），并写到出站请求头 {@code X-Trace-Id} 让下游服务也能读到（不依赖
 *       W3C traceparent 的兼容性兜底）。</li>
 * </ol>
 *
 * <p><b>为什么还要单独写 X-Trace-Id</b>：Micrometer Tracing 自动注入的是 W3C 标准
 * {@code traceparent} / {@code tracestate}，业务代码直接读 {@code X-Trace-Id} 更直观。
 * 这是和阿里云 ARMS、SkyWalking header 兼容的约定，迁移成本最低。</p>
 *
 * <p><b>位置</b>：{@code Order = HIGHEST_PRECEDENCE + 20}，排在 {@link RequestIdRelayFilter}
 * （{@code HIGHEST_PRECEDENCE}）之后，确保 RequestId 已写入 MDC（便于日志同时看到两个 ID）。</p>
 *
 * @author R13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceContextFilter implements GlobalFilter, Ordered {

    /** 响应 / 出站请求统一透传的 trace 头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** SLF4J MDC key，与 logback.xml 的 {@code %X{traceId}} 对应。 */
    public static final String MDC_TRACE_ID = "traceId";
    /** Span ID 同步写出，便于在 trace 内定位具体操作。 */
    public static final String MDC_SPAN_ID = "spanId";

    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.优先取 Micrometer Tracing 当前激活 span 的 traceId（OTel bridge 已自动从
        //   W3C traceparent header 解析或新建）。取不到则兜底生成。
        String traceId = resolveTraceId();
        String spanId = resolveSpanId();

        // 2.写 MDC（同步到 reactor 线程靠 ContextSnapshot，下游链式调用也能取到）
        MDC.put(MDC_TRACE_ID, traceId);
        if (spanId != null) {
            MDC.put(MDC_SPAN_ID, spanId);
        }

        // 3.出站请求头：把 X-Trace-Id 带给下游服务（即便它没启用 Micrometer Tracing 也能读到）
        final String finalTraceId = traceId;
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();

        // 4.响应头：让客户端（前端 / 第三方）也能拿到 traceId，方便排查工单关联
        mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);

        log.debug("[Trace] gateway relay traceId={} path={}", finalTraceId,
                exchange.getRequest().getPath().value());

        // 5.放行，结束后清掉 MDC（防止 WebFlux 线程复用造成脏数据）
        return chain.filter(mutatedExchange)
                .doFinally(signal -> {
                    MDC.remove(MDC_TRACE_ID);
                    MDC.remove(MDC_SPAN_ID);
                });
    }

    private String resolveTraceId() {
        Span current = tracer.currentSpan();
        if (current != null) {
            String id = current.context().traceId();
            if (id != null && !id.isEmpty() && !"00000000000000000000000000000000".equals(id)) {
                return id;
            }
        }
        // 兜底：当前没激活 span（极端情况下 Micrometer 没接管），用 UUID 顶一个
        return UUID.randomUUID().toString(true);
    }

    private String resolveSpanId() {
        Span current = tracer.currentSpan();
        return current != null ? current.context().spanId() : null;
    }

    @Override
    public int getOrder() {
        // HIGHEST_PRECEDENCE = RequestIdRelayFilter；我们排它后面，确保 RequestId 先就位
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}