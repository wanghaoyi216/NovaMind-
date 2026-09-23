package com.novamind.common.autoconfigure.refreshscope;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>@RefreshScope 装配入口 (R12 配置中心改造)</h1>
 *
 * <p>把 {@link RuntimeConfigProperties} 声明成 {@code @RefreshScope} 作用域。
 * 装配时机：被各业务服务的 SpringBoot 启动类通过 spring.factories / AutoConfiguration.imports 间接加载。</p>
 *
 * <p><b>为什么必须 @RefreshScope？</b></p>
 * <ol>
 *   <li>Nacos 推送 {@code ConfigurationProperties} 变更时，
 *       {@code CloudConfigService} 抛出 {@code EnvironmentChangeEvent}；</li>
 *   <li>没加 @RefreshScope 的 {@code @ConfigurationProperties} bean
 *       <b>不会</b>重新绑定——值仍在启动时冻结的字段里；</li>
 *   <li>加了 @RefreshScope 后，{@code ContextRefresher} 销毁旧 bean、按新配置重建，
 *       注入它的消费者（@Autowired）会拿到新实例。</li>
 * </ol>
 *
 * <p><b>陷阱（面试常考点）：</b></p>
 * <ul>
 *   <li>{@code @RefreshScope} 不能用在 {@code @ConfigurationProperties} 的 setter 直接持有的字段上——
 *       它作用于 bean，所以必须把 {@code @ConfigurationProperties} 本身装到 {@code @RefreshScope} bean 里。</li>
 *   <li>队列、线程池、HikariCP 等启动期资源不会随 @RefreshScope 重建；
 *       DB 密码变了需要 {@code /actuator/refresh} 触发 datasource 重建（spring-cloud-alibaba 默认开启）。</li>
 *   <li>{@code @Value} 注入的字段，{@code Environment} 是热更新的，但注入点本身的引用还是旧的——
 *       必须通过 {@code Environment} 或 {@code @RefreshScope} bean 才能拿到新值。</li>
 * </ul>
 *
 * <p>本类在 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 中声明，业务服务会自动引入。</p>
 *
 * @author R12-refactor
 */
@Configuration
@EnableConfigurationProperties(RuntimeConfigProperties.class)
public class RuntimeConfigAutoConfiguration {

    /**
     * 把 @ConfigurationProperties 再包一层为 @RefreshScope 作用域 bean。
     * Spring 会自动绑定 {@code @EnableConfigurationProperties(RuntimeConfigProperties.class)} 的元数据
     * 到本方法返回的类型上，所以这里直接返回 new 出来的类即可。
     *
     * <p>使用 {@code @RefreshScope} 必须引入 {@code spring-cloud-context}（已在父 BOM 依赖链中）。</p>
     */
    @Bean
    @RefreshScope
    public RuntimeConfigProperties tjRefreshableRuntimeConfig() {
        return new RuntimeConfigProperties();
    }
}
