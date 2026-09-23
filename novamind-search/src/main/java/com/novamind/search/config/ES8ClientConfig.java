package com.novamind.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

/**
 * R25 ES8 升级：Elasticsearch Java API Client 8.x 客户端配置
 * <p>
 * 取代旧版 {@code RestHighLevelClient}（已在 8.x 移除），使用官方推荐的
 * 强类型 {@link ElasticsearchClient}，所有 API 走 builder + lambda 形式，
 * 编译期即可发现 schema 错误。
 * <p>
 * 兼容 7.x 索引：7.x 与 8.x 默认索引格式（_source、_doc 路由）保持兼容，
 * 因此旧索引免重建即可被新客户端读取与检索。
 *
 * @author novamind
 */
@Slf4j
@Configuration
public class ES8ClientConfig {

    @Resource
    private ElasticsearchProperties elasticsearchProperties;

    /**
     * 底层 RestClient：基于 Apache HttpClient 5.x，与 8.x 服务端 TLS/Http 兼容
     * <p>bean 名称固定为 {@code elasticsearchRestClient}，覆盖 Spring Boot
     * {@code ElasticsearchRestClientAutoConfiguration} 的默认 RestClient（{@code @ConditionalOnMissingBean}）。
     */
    @Bean
    public RestClient elasticsearchRestClient() {
        List<String> uris = elasticsearchProperties.getUris();
        HttpHost[] hosts = uris.stream()
                .map(this::parseHost)
                .toArray(HttpHost[]::new);
        log.info("[ES8] 初始化 RestClient，节点: {}", List.of(hosts));
        return RestClient.builder(hosts).build();
    }

    /**
     * 传输层：Jackson 序列化 + RestClient 通信
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
    }

    /**
     * 强类型 Java API Client — 8.x 官方推荐入口
     * <p>bean 名称固定为 {@code elasticsearchClient}，覆盖 Spring Boot 默认 client。
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    private HttpHost parseHost(String uri) {
        URI u = URI.create(uri);
        String scheme = u.getScheme() == null ? "http" : u.getScheme();
        int port = u.getPort() < 0 ? 9200 : u.getPort();
        return new HttpHost(u.getHost(), port, scheme);
    }
}
