package com.novamind.common.autoconfigure.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * <h1>幂等基础设施自动化配置 (P0 改造)</h1>
 *
 * <p>本类仅负责：</p>
 * <ol>
 *   <li>注册 {@link IdempotencyProperties}（@ConfigurationProperties 绑定）</li>
 *   <li>导入 {@link IdempotentAspect}（@Idempotent 注解支持）</li>
 * </ol>
 *
 * <p>注意：拦截器 {@link IdempotencyKeyInterceptor} <b>不</b>在本类里注册。
 * 由各业务微服务自己在 {@code config/WebConfig.java} 里调用
 * {@code registry.addInterceptor(...)}，这样不同微服务可以精细控制要拦截的路径，
 * 避免一刀切影响如支付回调 notify、xxl-job 等不需要幂等的接口。</p>
 *
 * @author P0-refactor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(IdempotencyProperties.class)
@Import(IdempotentAspect.class) // 把 @Idempotent 切面拉起来（novamind-common 不在业务方 component-scan 路径下）
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "novamind.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyAutoConfiguration {
    // 占位：拦截器注册交给业务方的 WebConfig。详见类级 javadoc。
}
