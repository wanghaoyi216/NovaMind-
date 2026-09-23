package com.novamind.common.autoconfigure.tracing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * <h2>OpenTelemetry 追踪配置 (R13)</h2>
 *
 * <p>绑定 {@code novamind.tracing.*} 配置项，控制 OTLP gRPC exporter 行为。
 * 业务侧无需关心 Spring Boot {@code management.*} 原生属性，本类提供更紧凑的命名空间。</p>
 *
 * <p><b>配置示例</b>（{@code application-otel.yml}）：</p>
 * <pre>
 * novamind:
 *   tracing:
 *     enabled: true
 *     endpoint: http://localhost:4317
 *     sampling-probability: 1.0
 *     service-name: pay-service    # 不填则取 spring.application.name
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "novamind.tracing")
public class OpenTelemetryProperties {

    /** 总开关；默认 true。设为 false 时整条追踪链路完全关闭（包括 MDC 注入）。 */
    private boolean enabled = true;

    /** OTLP gRPC collector 地址，默认 4317。 */
    private String endpoint = "http://localhost:4317";

    /**
     * 采样率：0.0~1.0。
     * <ul>
     *   <li>1.0 = 全采样（开发/压测用）</li>
     *   <li>0.1 = 10% 采样（生产推荐）</li>
     *   <li>0.0 = 全关闭（紧急止血用）</li>
     * </ul>
     */
    private float samplingProbability = 1.0f;

    /**
     * 单次导出超时。OTLP gRPC 默认 10s；批量导出场景下建议适当放大。
     */
    private Duration exportTimeout = Duration.ofSeconds(10);

    /**
     * 是否在响应头透传 {@code X-Trace-Id}。
     * <p>默认 false：网关层（TraceContextFilter）会主动写，业务服务无需重复设置。</p>
     */
    private boolean exposeTraceIdHeader = false;
}