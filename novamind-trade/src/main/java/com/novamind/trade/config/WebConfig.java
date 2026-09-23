package com.novamind.trade.config;

import com.novamind.common.autoconfigure.idempotency.IdempotencyKeyInterceptor;
import com.novamind.common.autoconfigure.idempotency.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <h1>Web MVC 配置 (P0 改造)</h1>
 *
 * <p>显式注册 {@link IdempotencyKeyInterceptor}，
 * 针对 {@code /orders/**}、{@code /carts/**}、{@code /refund-applies/**}、
 * {@code /pay/**} 等写接口做幂等保护。</p>
 *
 * <p>为什么显式注册而不是用 AutoConfiguration：</p>
 * <ul>
 *   <li>订单服务对幂等的覆盖范围有自己的判断（哪些路径要、哪些不要）</li>
 *   <li>和已有的 {@code auth-resource-sdk} 拦截器分开维护，order 更可控</li>
 *   <li>避免对所有微服务一刀切</li>
 * </ul>
 *
 * @author P0-refactor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IdempotencyProperties properties;
    /** 用 ObjectProvider 而不是直接 @Resource，避免 Redis 不可用时启动挂掉 */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.isEnabled() || !properties.isInterceptorEnabled()) {
            log.info("[trade-WebConfig] 幂等拦截器已关闭，跳过注册");
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.warn("[trade-WebConfig] StringRedisTemplate 不在 classpath，跳过幂等拦截器注册");
            return;
        }
        registry.addInterceptor(new IdempotencyKeyInterceptor(redis, properties))
                // 只对写接口生效，GET 查询类不走幂等
                .addPathPatterns(
                        "/orders/**",
                        "/carts/**",
                        "/refund-applies/**",
                        "/pay/**"
                )
                // 高优先级，比 LoginAuthInterceptor 更早触发
                .order(Integer.MIN_VALUE + 100);

        log.info("[trade-WebConfig] IdempotencyKeyInterceptor 已注册, paths=/orders/**, /carts/**, /refund-applies/**, /pay/**");
    }
}
