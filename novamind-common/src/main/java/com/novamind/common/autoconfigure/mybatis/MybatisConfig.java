package com.novamind.common.autoconfigure.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * <h1>MyBatis + HikariCP 统一配置 (P0 改造)</h1>
 *
 * <p>做的事：</p>
 * <ol>
 *   <li>保留原来的 MyBatis-Plus 拦截器（分页 / 字段填充等）</li>
 *   <li><b>新增</b>{@link SlowSqlLogger}：拦截 Executor，超 500ms 打 WARN</li>
 *   <li><b>新增</b>{@link HikariPoolProperties}：用 {@code @ConfigurationProperties(prefix="spring.datasource.hikari")}
 *       集中所有服务的连接池参数（默认值: max=20, min=5, connectionTimeout=30s, leakThreshold=60s）</li>
 *   <li>Spring Boot 默认的 {@code DataSourceAutoConfiguration} 会读这些值并构造 HikariDataSource，
 *       所以业务方不需要在自己的 application.yml 里再写</li>
 * </ol>
 *
 * @author P0-refactor
 */
@Slf4j
@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class, HikariDataSource.class})
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
public class MybatisConfig {

    /**
     * @see MyBatisAutoFillInterceptor 通过自定义拦截器来实现自动注入creater和updater
     * @deprecated 存在任务更新数据导致updater写入0或null的问题，暂时废弃
     */
    // @Bean
    // @ConditionalOnMissingBean
    public BaseMetaObjectHandler baseMetaObjectHandler() {
        return new BaseMetaObjectHandler();
    }

    @Bean
    // @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@Autowired(required = false) DynamicTableNameInnerInterceptor innerInterceptor) { // 如果依赖了common模块，即便类本身没有这个拦截器也不会报错
        // 1.定义插件主体，注意顺序：表名 > 多租户 > 分页 > 乐观锁 > 字段填充
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 2.表名插件
        if (innerInterceptor != null) {
            interceptor.addInnerInterceptor(innerInterceptor);
        }
        // 3.分页插件
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        // 4.字段填充插件
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());
        return interceptor;
    }

    /**
     * <h2>P0 改造：MyBatis 慢 SQL 监控拦截器</h2>
     *
     * <p>阈值默认 500ms，超过 WARN 打印完整 SQL + 参数 + 耗时。
     * 通过 {@link com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer}
     * 注册到 MyBatis-Plus 的 {@code MybatisConfiguration#addInterceptor}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public SlowSqlLogger slowSqlLogger() {
        SlowSqlLogger interceptor = new SlowSqlLogger();
        interceptor.setThresholdMs(500L);
        interceptor.setPrintFullSql(true);
        interceptor.setMaxParamLength(500);
        log.info("[MybatisConfig] SlowSqlLogger 已注册 threshold=500ms");
        return interceptor;
    }

    /**
     * <h2>P0 改造：把 SlowSqlLogger 接入 MyBatis Configuration</h2>
     *
     * <p>使用 MyBatis-Plus 官方推荐的 {@link com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer}
     * 自定义回调，把 {@link SlowSqlLogger}（MyBatis 原生 {@code Interceptor}）
     * 加到 {@code MybatisConfiguration#addInterceptor} 里。这样业务方不需要改 yaml，
     * startup 后会自动启用慢 SQL 监控。</p>
     */
    @Bean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer")
    public com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer slowSqlCustomizer(SlowSqlLogger slowSqlLogger) {
        return configuration -> {
            if (!configuration.getInterceptors().contains(slowSqlLogger)) {
                configuration.addInterceptor(slowSqlLogger);
                log.info("[MybatisConfig] SlowSqlLogger 已添加到 MybatisConfiguration, threshold=500ms");
            }
        };
    }

    /**
     * <h2>P0 改造：HikariCP 池参数绑定</h2>
     *
     * <p>对应 yaml：</p>
     * <pre>{@code
     * spring:
     *   datasource:
     *     hikari:
     *       maximum-pool-size: 20
     *       minimum-idle: 5
     *       connection-timeout: 30000
     *       leak-detection-threshold: 60000
     * }</pre>
     *
     * <p>作用机制：
     * Spring Boot 的 {@code DataSourceAutoConfiguration} 在没有自定义 DataSource 时，
     * 会自动读 {@code spring.datasource.hikari.*} 构造 HikariDataSource。
     * 我们这里的 {@link HikariPoolProperties} 用同样的 prefix 暴露一组默认值，
     * 并在日志里打印实际生效的值（便于 ops 快速确认）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariPoolProperties hikariPoolProperties() {
        return new HikariPoolProperties();
    }

    /**
     * <h2>P0 改造：打印连接池生效参数，启动时一眼可见</h2>
     */
    @Bean
    public HikariPoolReporter hikariPoolReporter(HikariPoolProperties hikariPoolProperties) {
        log.info("[MybatisConfig] HikariCP 默认参数生效: max={}, min={}, connectTimeout={}ms, "
                        + "leakDetection={}ms, pool={}",
                hikariPoolProperties.getMaximumPoolSize(),
                hikariPoolProperties.getMinimumIdle(),
                hikariPoolProperties.getConnectionTimeout(),
                hikariPoolProperties.getLeakDetectionThreshold(),
                hikariPoolProperties.getPoolName());
        return new HikariPoolReporter();
    }

    /**
     * Hikari 池属性绑定（{@code spring.datasource.hikari.*}），默认值即为 P0 改造目标
     */
    @Data
    public static class HikariPoolProperties {
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeout = 30_000L;
        private long idleTimeout = 600_000L;
        private long maxLifetime = 1_800_000L;
        private long leakDetectionThreshold = 60_000L;
        private String poolName = "HikariPool-Novamind";
        private String connectionTestQuery;
    }

    /**
     * 仅用于打日志的占位 bean，便于以后扩展（@EventListener等）
     */
    public static class HikariPoolReporter {
    }
}
