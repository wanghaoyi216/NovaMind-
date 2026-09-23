package com.novamind.common.autoconfigure.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * <h1>R15 HikariCP 自动装配</h1>
 *
 * <p>做的事：</p>
 * <ol>
 *   <li>绑定 {@link HikariProperties}（{@code novamind.datasource.hikari.*}）</li>
 *   <li>启动期把自动算出的 {@code maximumPoolSize} 写到
 *       Spring Boot DataSourceAutoConfiguration 的同名属性
 *       （{@code spring.datasource.hikari.maximum-pool-size}），
 *       这样 {@link DataSource}（{@link HikariDataSource}）初始化时直接拿到我们算好的值</li>
 *   <li>连接池启动后打印生效参数，运维一眼可见</li>
 * </ol>
 *
 * <h3>调优公式</h3>
 * <pre>{@code
 *   maximumPoolSize = Runtime.availableProcessors() * 2 + 1
 * }</pre>
 *
 * <p>为什么不是 "核数 * N"？HikariCP Wiki 给出该公式的依据：</p>
 * <ul>
 *   <li>数据库连接大部分时间都在等 IO（CPU 利用率极低），所以池大小不需要和 CPU 1:1；</li>
 *   <li>经验值：每个连接平均带来约 1 个待处理请求，过大反而增加上下文切换与锁竞争；</li>
 *   <li>实测中 {@code cores*2+1} 在多数 OLTP 负载下能取得最佳吞吐量。</li>
 * </ul>
 *
 * <h3>为什么把值写到 {@code spring.datasource.hikari.*}</h3>
 * <p>Spring Boot 的 {@code DataSourceAutoConfiguration} 在没有自定义 {@code DataSource} bean 时，
 * 会读 {@code spring.datasource.hikari.*} 构造 {@code HikariDataSource}。我们这里只是把
 * {@code HikariProperties} 算出的 pool size 同步给 Spring Boot 的绑定上下文，
 * 业务方不需要在自己 application.yml 里再写一遍。</p>
 *
 * @author R15-refactor
 *
 * <p><b>排序约束：</b>本类必须排在 {@link DataSourceAutoConfiguration} 之前（否则
 * {@link #afterPropertiesSet()} 下发的 {@code spring.datasource.hikari.*} 赶不上被读取）。
 * 注意不要再声明 {@code @AutoConfigureAfter(MybatisConfig)}：{@code MybatisConfig} 声明了
 * {@code @AutoConfigureAfter(DataSourceAutoConfiguration)}，三者会构成
 * {@code Hikari → DataSource → Mybatis → Hikari} 的闭环，Spring Boot 启动期会直接抛
 * {@code AutoConfigure cycle detected}。本类不依赖 {@code MybatisConfig} 的任何 bean，
 * 该边本来就不必要。</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(prefix = "novamind.datasource.hikari", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(HikariProperties.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class HikariAutoConfiguration implements InitializingBean {

    private final HikariProperties properties;
    /**
     * 启动时同步给 Spring Environment 的 key（前缀 + 属性名）。
     * Spring Boot 的 {@code DataSourceAutoConfiguration} 会从这里读。
     */
    private static final String SB_HIKARI_PREFIX = "spring.datasource.hikari";

    public HikariAutoConfiguration(HikariProperties properties) {
        this.properties = properties;
    }

    /**
     * 在所有 properties bean 绑定完成后执行 ——
     * 把 {@link HikariProperties#resolvePoolSize()} 算出来的值同步到
     * {@code spring.datasource.hikari.maximum-pool-size}，
     * Spring Boot 的 {@code DataSourceAutoConfiguration} 会用它构造 HikariDataSource。
     */
    @Override
    public void afterPropertiesSet() {
        int poolSize = properties.resolvePoolSize();
        System.setProperty(SB_HIKARI_PREFIX + ".maximum-pool-size", String.valueOf(poolSize));
        // 给 minimum-idle 也同步一下，避免 Spring Boot 默认值与 Nacos 不一致
        System.setProperty(SB_HIKARI_PREFIX + ".minimum-idle", String.valueOf(properties.getMinimumIdle()));
        System.setProperty(SB_HIKARI_PREFIX + ".connection-timeout", String.valueOf(properties.getConnectionTimeout()));
        System.setProperty(SB_HIKARI_PREFIX + ".validation-timeout", String.valueOf(properties.getValidationTimeout()));
        System.setProperty(SB_HIKARI_PREFIX + ".max-lifetime", String.valueOf(properties.getMaxLifetime()));
        System.setProperty(SB_HIKARI_PREFIX + ".idle-timeout", String.valueOf(properties.getIdleTimeout()));
        System.setProperty(SB_HIKARI_PREFIX + ".leak-detection-threshold", String.valueOf(properties.getLeakDetectionThreshold()));
        System.setProperty(SB_HIKARI_PREFIX + ".pool-name", properties.getPoolName());
        System.setProperty(SB_HIKARI_PREFIX + ".auto-commit", String.valueOf(properties.isAutoCommit()));
        if (properties.getConnectionTestQuery() != null && !properties.getConnectionTestQuery().isEmpty()) {
            System.setProperty(SB_HIKARI_PREFIX + ".connection-test-query", properties.getConnectionTestQuery());
        }
        log.info("[HikariAutoConfiguration] R15 HikariCP 参数已下发到 {}, cores={}, poolSize={}, "
                        + "maxLifetime={}ms, connectionTimeout={}ms, validationTimeout={}ms, leakThreshold={}ms",
                SB_HIKARI_PREFIX,
                Runtime.getRuntime().availableProcessors(),
                poolSize,
                properties.getMaxLifetime(),
                properties.getConnectionTimeout(),
                properties.getValidationTimeout(),
                properties.getLeakDetectionThreshold());
    }
}