package com.novamind.aigc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * aigc 类路径守护：novamind-common 的 LocalOutboxService 是 @Service（构造依赖
 * RabbitTemplate），而 spring-amqp/rabbit 在 common 中为 provided 作用域、不会传递到
 * aigc。该类随 {@code scanBasePackages = "com.novamind"} 被误扫入本容器，创建时即抛
 * NoClassDefFoundError: org/springframework/amqp/core/ReturnedMessage。
 *
 * <p>MQ 能力的正规装配入口是 common 的 MqConfig（@ConditionalOnClass 守护、经
 * AutoConfiguration.imports 注册），无 amqp 时本就应整体退避；这里仅在容器实例化
 * 阶段之前移除误扫入的直接注册，不改变其余扫描语义。若日后 aigc 引入 amqp 依赖，
 * 可删除本类以恢复 outbox 装配。</p>
 */
@Slf4j
@Configuration
public class MqScanGuardConfig {

    /** 故意用字符串常量比对，避免在无 amqp 的类路径上触碰该类。 */
    private static final String LOCAL_OUTBOX_SERVICE_FQCN = "com.novamind.common.autoconfigure.mq.LocalOutboxService";

    @Bean
    public static BeanFactoryPostProcessor mqOutboxScanGuard() {
        return beanFactory -> {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                return;
            }
            for (String name : registry.getBeanDefinitionNames()) {
                if (LOCAL_OUTBOX_SERVICE_FQCN.equals(registry.getBeanDefinition(name).getBeanClassName())) {
                    registry.removeBeanDefinition(name);
                    log.info("已移除误扫描的 MQ outbox bean 定义: {} (aigc 无 amqp 依赖)", name);
                }
            }
        };
    }
}
