package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * R27 ShardingSphere 5.4.x 分库分表数据源配置
 * <p>
 * 采用 5.4.x 推荐的 YAML + DistSQL 模式:
 *   1. 静态规则写 classpath:sharding-rules.yaml
 *   2. 通过 YamlEngine 解析为 Map
 *   3. ShardingSphereDataSourceFactory 创建 ShardingSphereDataSource (内置归并引擎 + Hint 路由)
 * </p>
 * <p>
 * 5.4.x 较 4.x 的关键变化:
 *   - DataSource 由 DriverManagerDataSource 改为 ShardingSphereDataSource
 *   - 规则加载 API: ShardingSphereDataSourceFactory.createDataSource(yamlRootMap, props, ...)
 *   - HintManager 用 ThreadLocal 持有 (替代 4.x 的 HintManagerHolder)
 * </p>
 *
 * @author R27
 */
@Slf4j
@Configuration
public class DataSourceShardingConfig {

    /** 业务库名 (MyBatis-Plus 多数据源时使用) */
    public static final String SHARDING_DS_NAME = "shardingDs";

    /**
     * 主数据源: ShardingSphere 代理的逻辑 DataSource
     * MyBatis-Plus 通过 @Qualifier("shardingDs") 注入此数据源
     */
    @Bean(name = SHARDING_DS_NAME)
    @Primary
    public DataSource shardingDataSource() throws SQLException, IOException {
        log.info("====== R27 初始化 ShardingSphere 5.4.x 分库分表数据源 ======");

        // 1. 加载 classpath:sharding-rules.yaml
        Map<String, Object> yamlRoot = loadYamlRules("sharding-rules.yaml");
        log.info("[R27] sharding-rules.yaml 加载完成, 顶层节点: {}", yamlRoot.keySet());

        // 2. 解析 dataSources (5.4.x 改为 Map<String, DataSource>)
        @SuppressWarnings("unchecked")
        Map<String, Object> dataSourceMap = (Map<String, Object>) yamlRoot.get("dataSources");
        Map<String, DataSource> realDataSources = new HashMap<>(dataSourceMap.size());

        dataSourceMap.forEach((name, conf) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) conf;
            realDataSources.put(name, buildHikariDataSource(name, props));
            log.info("[R27]  注册物理数据源: {}", name);
        });

        // 3. 提取 rules 和 props
        @SuppressWarnings("unchecked")
        Map<String, Object> rulesMap = (Map<String, Object>) yamlRoot.get("rules");
        @SuppressWarnings("unchecked")
        Map<String, Object> propsMap = (Map<String, Object>) yamlRoot.get("props");
        Properties globalProps = new Properties();
        if (propsMap != null) {
            propsMap.forEach((k, v) -> globalProps.setProperty(k, String.valueOf(v)));
        }

        // 4. 5.4.x 推荐 API: 传入 yamlMap + props + 真实数据源
        DataSource dataSource = ShardingSphereDataSourceFactory.createDataSource(
                wrapYamlMap(yamlRoot),
                globalProps,
                realDataSources
        );

        log.info("====== R27 ShardingSphere 5.4.x 数据源初始化完成 ======");
        return dataSource;
    }

    /**
     * 5.4.x YamlEngine 要求将规则/数据源分离开, 包装成 ShardingSphere 期望的格式
     */
    private Map<String, Object> wrapYamlMap(Map<String, Object> yamlRoot) {
        Map<String, Object> wrapped = new HashMap<>();
        if (yamlRoot.containsKey("rules")) {
            wrapped.put("rules", yamlRoot.get("rules"));
        }
        if (yamlRoot.containsKey("props")) {
            wrapped.put("props", yamlRoot.get("props"));
        }
        if (yamlRoot.containsKey("authority")) {
            wrapped.put("authority", yamlRoot.get("authority"));
        }
        return wrapped;
    }

    /**
     * 使用 ShardingSphere 自带的 YamlEngine 解析 (5.4.x 替代 SnakeYAML)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYamlRules(String classpathPath) throws IOException {
        Resource resource = new ClassPathResource(classpathPath);
        try (InputStreamReader reader = new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8)) {
            return YamlEngine.unmarshal(reader, Map.class);
        }
    }

    /**
     * 构建 Hikari 数据源 (5.4.x 推荐)
     */
    private DataSource buildHikariDataSource(String name, Map<String, Object> conf) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setPoolName("HikariPool-" + name);
        ds.setJdbcUrl(strValue(conf, "jdbcUrl"));
        ds.setUsername(strValue(conf, "username"));
        ds.setPassword(strValue(conf, "password"));
        ds.setDriverClassName(strValue(conf, "driverClassName"));
        if (conf.containsKey("maxPoolSize")) {
            ds.setMaximumPoolSize(intValue(conf, "maxPoolSize"));
        }
        if (conf.containsKey("minPoolSize")) {
            ds.setMinimumPoolSize(intValue(conf, "minPoolSize"));
        }
        if (conf.containsKey("connectionTimeout")) {
            ds.setConnectionTimeout(longValue(conf, "connectionTimeout"));
        }
        if (conf.containsKey("idleTimeout")) {
            ds.setIdleTimeout(longValue(conf, "idleTimeout"));
        }
        return ds;
    }

    private String strValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("R27 数据源配置缺少字段: " + key);
        }
        return v.toString();
    }

    private int intValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
    }

    private long longValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
    }

    /**
     * ShardingSphere 全局配置属性 (供 DistSQL 动态下发 / 运维)
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.shardingsphere")
    public Properties shardingSphereProperties() {
        return new Properties();
    }
}
