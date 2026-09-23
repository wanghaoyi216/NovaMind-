package com.novamind.common.autoconfigure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novamind.common.cache.CacheAsideProperties;
import com.novamind.common.cache.CacheAsideTemplate;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.novamind.common.autoconfigure.redisson.RedissonConfig;

/**
 * <h1>R23 CacheAsideTemplate 装配入口</h1>
 *
 * <p>条件：classpath 下有 {@link RedissonClient}，并且 Spring 容器里已经有
 * RedissonClient + ObjectMapper bean（由 RedissonConfig / SpringBoot 默认提供）。</p>
 *
 * <p>注册 {@link CacheAsideProperties} + {@link CacheAsideTemplate} 两个 bean，
 * 业务服务 {@code @Autowired} {@link CacheAsideTemplate} 即可使用。</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass({RedissonClient.class, CacheAsideTemplate.class})
@ConditionalOnBean(RedissonClient.class)
@AutoConfigureAfter(RedissonConfig.class)
@EnableConfigurationProperties(CacheAsideProperties.class)
public class CacheAsideAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheAsideTemplate cacheAsideTemplate(RedissonClient redissonClient,
                                                 ObjectMapper objectMapper,
                                                 CacheAsideProperties properties) {
        log.info("[CacheAside] 初始化 CacheAsideTemplate, nullTtl={}, lockTtl={}, jitter={}%",
                properties.getNullTtl(), properties.getLockTtl(), properties.getTtlJitterPercent());
        return new CacheAsideTemplate(redissonClient, objectMapper, properties);
    }
}
