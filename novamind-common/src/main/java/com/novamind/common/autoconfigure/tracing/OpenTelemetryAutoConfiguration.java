package com.novamind.common.autoconfigure.tracing;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * <h2>OpenTelemetry 分布式追踪自动装配 (R13)</h2>
 *
 * <p><b>装配条件</b>（三件套齐全才生效）：</p>
 * <ol>
 *   <li>{@code spring-boot-starter-actuator} —— 提供 {@code management.otlp.tracing.*} 自动配置入口</li>
 *   <li>{@code io.micrometer:micrometer-tracing-bridge-otel} —— Micrometer Observation ↔ OTel SDK 桥接</li>
 *   <li>{@code io.opentelemetry:opentelemetry-exporter-otlp} —— OTLP gRPC exporter（4317 端口）</li>
 * </ol>
 *
 * <p><b>职责边界</b>：</p>
 * <ul>
 *   <li>Spring Boot 原生 {@code OpenTelemetryAutoConfiguration} 负责创建 {@link OpenTelemetry} SDK 实例
 *       并装配 {@code OtlpGrpcSpanExporter}；</li>
 *   <li>本类 <b>不重复</b> 装配 OTel SDK，只负责：</li>
 *   <li>① 注册 {@link OpenTelemetryProperties}（项目命名空间 {@code novamind.tracing.*}）；</li>
 *   <li>② 暴露一个项目级 {@link Tracer} bean（scope = {@code com.novamind}），业务方注入即可手动埋点；</li>
 *   <li>③ 启动期打印采样率 / endpoint，便于排查追踪链路问题。</li>
 * </ul>
 *
 * <p><b>为什么不自己 new OpenTelemetrySdk</b>：Spring Boot 3.x 已提供完整自动配置，
 * 手写 SDK 反而会绕开 {@code management.tracing.sampling.probability} 等原生开关，得不偿失。</p>
 *
 * @author R13
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OpenTelemetryProperties.class)
@ConditionalOnClass(name = {
        "io.opentelemetry.api.OpenTelemetry",
        "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter",
        "io.micrometer.tracing.Tracer"
})
@ConditionalOnProperty(prefix = "novamind.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryAutoConfiguration {

    private final OpenTelemetryProperties properties;
    private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
    private final ObjectProvider<Tracer> tracerProvider;

    public OpenTelemetryAutoConfiguration(OpenTelemetryProperties properties,
                                          ObjectProvider<OpenTelemetry> openTelemetryProvider,
                                          ObjectProvider<Tracer> tracerProvider) {
        this.properties = properties;
        this.openTelemetryProvider = openTelemetryProvider;
        this.tracerProvider = tracerProvider;
    }

    @PostConstruct
    void announce() {
        log.info("[Tracing] OTel auto-config ready: endpoint={}, sampling={}, otelSdk={}, tracer={}",
                properties.getEndpoint(),
                properties.getSamplingProbability(),
                openTelemetryProvider.getIfAvailable() != null ? "present" : "absent",
                tracerProvider.getIfAvailable() != null ? "present" : "absent");
    }

    /*
     * 这里曾额外暴露一个 `novamindTracer` @Bean（方法签名 `Tracer novamindTracer(Tracer micrometerTracer)`），
     * 只是把 Spring Boot 已经装配好的 Tracer 原样返回，属于纯冗余，且会造成两处装配失败：
     *   1) 它使容器里出现两个 `Tracer` 类型 bean，按类型注入处直接抛
     *      NoUniqueBeanDefinitionException（网关的 TraceContextFilter 就是这样挂掉的）；
     *   2) 它自身按类型注入 `Tracer` 时可能选中自己，抛 BeanCurrentlyInCreationException
     *      （learning / trade 启动失败的原因）。
     * 全仓库无任何代码按名字引用 `novamindTracer`，需要埋点时直接按类型注入 Boot 提供的
     * Tracer 即可，因此这里不再声明该 bean。
     */
}