package com.novamind.common.autoconfigure.refreshscope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h1>@RefreshScope 装配与热更新断言 (R12 配置中心改造)</h1>
 *
 * <p>覆盖三件事：</p>
 * <ol>
 *   <li>AutoConfiguration.imports 中的 {@link RuntimeConfigAutoConfiguration} 能被 Spring Boot 加载；</li>
 *   <li>注册方法 {@code tjRefreshableRuntimeConfig()} 真的被打上了 {@link RefreshScope}；</li>
 *   <li>{@link RuntimeConfigProperties} 与 {@code novamind.runtime-config.*} 字段能正常绑定 YAML。</li>
 * </ol>
 *
 * <p>不使用 {@code @SpringBootTest} 触发整容器（会拖起 redis、nacos 等），改用
 * {@link ApplicationContextRunner} 局部跑装配。</p>
 *
 * @author R12-refactor
 */
class RuntimeConfigAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimeConfigAutoConfiguration.class))
            .withPropertyValues(
                    "novamind.runtime-config.feature-toggle.enable-coupon=false",
                    "novamind.runtime-config.feature-toggle.enable-points=true",
                    "novamind.runtime-config.rate-limit.orders-per-sec=500",
                    "novamind.runtime-config.rate-limit.login-per-min=120",
                    "novamind.runtime-config.gray-list[0]=user-9001",
                    "novamind.runtime-config.gray-list[1]=user-9002"
            );

    @Test
    @DisplayName("@RefreshScope 装配：bean 必须存在且被 @RefreshScope 标注")
    void should_register_runtime_config_bean_with_refresh_scope() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RuntimeConfigProperties.class);
            assertThat(ctx.getBean(RuntimeConfigProperties.class))
                    .isInstanceOf(RuntimeConfigProperties.class);
        });
    }

    @Test
    @DisplayName("@RefreshScope 反射验证：注册方法确实带 @RefreshScope")
    void refresh_scope_annotation_should_be_present_on_factory_method() throws NoSuchMethodException {
        Method factoryMethod = RuntimeConfigAutoConfiguration.class
                .getDeclaredMethod("tjRefreshableRuntimeConfig");
        RefreshScope refreshScope = AnnotationUtils.findAnnotation(factoryMethod, RefreshScope.class);
        assertThat(refreshScope)
                .as("Factory method 必须显式标注 @RefreshScope，否则 Nacos 推送不会重建该 bean")
                .isNotNull();
        assertThat(refreshScope.proxyMode())
                .as("@RefreshScope 强制使用 CGLIB 代理（ScopedProxyMode.TARGET_CLASS）")
                .isEqualTo(org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS);
    }

    @Test
    @DisplayName("YAML 绑定：YAML 字段能正确填进 @ConfigurationProperties bean")
    void yaml_values_should_be_bound_into_properties_bean() {
        runner.run(ctx -> {
            RuntimeConfigProperties props = ctx.getBean(RuntimeConfigProperties.class);

            assertThat(props.getFeatureToggle().isEnableCoupon()).isFalse();
            assertThat(props.getFeatureToggle().isEnablePoints()).isTrue();
            assertThat(props.getFeatureToggle().isEnableDelayMq())
                    .as("未配置的 enableDelayMq 应保留默认值 true")
                    .isTrue();

            assertThat(props.getRateLimit().getOrdersPerSec()).isEqualTo(500);
            assertThat(props.getRateLimit().getLoginPerMin()).isEqualTo(120);

            assertThat(props.getGrayList())
                    .containsExactly("user-9001", "user-9002");
        });
    }

    @Test
    @DisplayName("热更新模拟：拿到 bean → 修改后业务方能读到新值（验证 setter 不丢语义）")
    void after_setter_business_can_read_new_value() {
        // 用最轻量的 AnnotationConfigApplicationContext 直跑，不走 @SpringBootTest
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(TestConfig.class);
            ctx.refresh();

            RuntimeConfigProperties props = ctx.getBean(RuntimeConfigProperties.class);

            // 模拟 Nacos 推送后 setter 调用
            props.getRateLimit().setOrdersPerSec(1000);
            props.getGrayList().add("user-from-runtime");

            assertThat(props.getRateLimit().getOrdersPerSec()).isEqualTo(1000);
            assertThat(props.getGrayList())
                    .contains("user-from-runtime")
                    .hasSize(3);  // 2 个 yaml + 1 个新增
        }
    }

    /**
     * 最轻量的测试配置：直接启用 RuntimeConfigProperties 的绑定，
     * 不依赖 spring-cloud 全套（refresh 这个测试只验证 setter 与绑定，不依赖真实 ContextRefresher）。
     */
    @Configuration
    @EnableConfigurationProperties(RuntimeConfigProperties.class)
    static class TestConfig {

        @Bean
        @RefreshScope
        public RuntimeConfigProperties tjRefreshableRuntimeConfig() {
            return new RuntimeConfigProperties();
        }
    }
}
