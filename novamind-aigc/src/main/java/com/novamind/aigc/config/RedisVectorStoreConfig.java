package com.novamind.aigc.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

/**
 * Redis 向量库手动装配。
 *
 * <p>背景：spring-ai 1.0.0 的 RedisVectorStoreAutoConfiguration 以
 * {@code @ConditionalOnBean(JedisConnectionFactory.class)} 作为开关，但缺少
 * {@code @AutoConfigureAfter(RedisAutoConfiguration.class)} 排序注解，导致条件
 * 在 Boot Redis 自动配置注册连接工厂之前评估，vectorStore Bean 永远不会创建，
 * DocumentServiceImpl / EmbeddingController / AdvisorFactory 等 VectorStore 注入点
 * 启动即失败。
 *
 * <p>本类复刻上游自动配置 vectorStore(...) 的同款构造逻辑
 * （JedisConnectionFactory -> JedisPooled -> RedisVectorStore），通过方法参数
 * 注入强制正确的 Bean 初始化顺序；配置项沿用 spring.ai.vectorstore.redis.*，
 * 默认值与 RedisVectorStore.DEFAULT_INDEX_NAME / DEFAULT_PREFIX 一致。
 */
@Configuration
public class RedisVectorStoreConfig {

    @Value("${spring.ai.vectorstore.redis.index-name:spring-ai-index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix:spring-ai-redis:}")
    private String prefix;

    @Value("${spring.ai.vectorstore.redis.initialize-schema:false}")
    private boolean initializeSchema;

    /**
     * Bean 名与返回类型均与上游自动配置可互斥（其内部有 @ConditionalOnMissingBean）。
     */
    @Bean
    public RedisVectorStore redisVectorStore(JedisConnectionFactory connectionFactory, EmbeddingModel embeddingModel) {
        JedisPooled jedisPooled = new JedisPooled(
                new HostAndPort(connectionFactory.getHostName(), connectionFactory.getPort()),
                DefaultJedisClientConfig.builder()
                        .ssl(connectionFactory.isUseSsl())
                        .clientName(connectionFactory.getClientName())
                        .timeoutMillis(connectionFactory.getTimeout())
                        .password(connectionFactory.getPassword())
                        .build());
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(this.indexName)
                .prefix(this.prefix)
                .initializeSchema(this.initializeSchema)
                .build();
    }
}
