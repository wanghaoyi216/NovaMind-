package com.novamind.common.autoconfigure.datasource;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>R15 慢查询拦截器装配</h1>
 *
 * <p>做的事：</p>
 * <ol>
 *   <li>注册 {@link SlowQueryInterceptor} bean（{@link MeterRegistry} 可选注入，
 *       空时只打 WARN 日志，不影响业务）；</li>
 *   <li>通过 MyBatis-Plus 的 {@link ConfigurationCustomizer} 把慢查询拦截器加到
 *       {@code MybatisConfiguration#addInterceptor} 里，业务方无需改 yaml；</li>
 *   <li>启动期打印生效阈值（默认 200ms）。</li>
 * </ol>
 *
 * <p>通过 {@code novamind.datasource.slow-query.enabled=false} 可整体关闭。</p>
 *
 * @author R15-refactor
 */
@Slf4j
@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, ConfigurationCustomizer.class})
@ConditionalOnProperty(prefix = "novamind.datasource.slow-query", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SlowQueryProperties.class)
public class SlowQueryAutoConfiguration {

    private final SlowQueryProperties properties;

    public SlowQueryAutoConfiguration(SlowQueryProperties properties) {
        this.properties = properties;
    }

    /**
     * 慢查询拦截器 bean。
     * Micrometer {@link MeterRegistry} 是 {@link ObjectProvider} 注入，业务服务不引入 actuator 时为 null，
     * 此时只走日志通道。
     */
    @Bean
    @ConditionalOnMissingBean
    public SlowQueryInterceptor slowQueryInterceptor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        SlowQueryInterceptor interceptor = new SlowQueryInterceptor();
        interceptor.setThresholdMs(properties.getThresholdMs());
        interceptor.setWarnLogEnabled(properties.isWarnLogEnabled());
        interceptor.setPrintFullSql(properties.isPrintFullSql());
        interceptor.setMaxParamLength(properties.getMaxParamLength());
        interceptor.setTraceIdKey(properties.getTraceIdKey());
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            interceptor.setMeterRegistry(registry);
            log.info("[SlowQueryAutoConfiguration] Micrometer MeterRegistry 已注入，"
                    + "慢查询指标将上报到 {}", registry.getClass().getSimpleName());
        } else {
            log.info("[SlowQueryAutoConfiguration] 未找到 MeterRegistry，仅启用 WARN 日志通道");
        }
        log.info("[SlowQueryAutoConfiguration] SlowQueryInterceptor 已注册 threshold={}ms",
                properties.getThresholdMs());
        return interceptor;
    }

    /**
     * 通过 MyBatis-Plus {@link ConfigurationCustomizer} 把 {@link SlowQueryInterceptor}
     * 接入到 MyBatis Configuration。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigurationCustomizer slowQueryConfigurationCustomizer(SlowQueryInterceptor slowQueryInterceptor) {
        return configuration -> {
            if (!configuration.getInterceptors().contains(slowQueryInterceptor)) {
                configuration.addInterceptor(slowQueryInterceptor);
                log.info("[SlowQueryAutoConfiguration] SlowQueryInterceptor 已绑定到 MybatisConfiguration");
            }
        };
    }
}