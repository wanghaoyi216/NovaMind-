package com.novamind.common.autoconfigure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novamind.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;

import static com.novamind.common.constants.Constant.REQUEST_ID_HEADER;
import static com.novamind.common.constants.MqConstants.Exchange.ERROR_EXCHANGE;
import static com.novamind.common.constants.MqConstants.Key.ERROR_KEY_PREFIX;
import static com.novamind.common.constants.MqConstants.Queue.ERROR_QUEUE_TEMPLATE;


/**
 * <h1>RabbitMQ 全局自动装配</h1>
 *
 * <p>P0 bugfix：此前第 45 行存在 {@code private final RabbitMqProperties rabbitMqProperties
 * = new RabbitMqProperties();} 手动 new 的本地实例，导致 DLX 相关三个 @Bean 读到的永远是
 * 默认值，shared-mq.yaml 里 {@code novamind.rabbitmq.*} 的任何覆盖都完全失效。
 * 现改为构造器注入 Spring 容器管理的单例（{@code @EnableConfigurationProperties} 注册的那个），
 * 与 {@link LocalOutboxService} 拿到的是同一份配置。</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(value = {MessageConverter.class, AmqpTemplate.class})
@EnableConfigurationProperties(RabbitMqProperties.class)
@Import(LocalOutboxService.class)
@RequiredArgsConstructor
public class MqConfig implements EnvironmentAware {

    private String defaultErrorRoutingKey;
    private String defaultErrorQueue;
    private String serviceName = "unknown";
    /** P0 bugfix：构造器注入容器内的 RabbitMqProperties 单例，不再本地 new */
    private final RabbitMqProperties rabbitMqProperties;

    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnProperty(prefix = "spring.rabbitmq.listener", name = "type", havingValue = "simple",
            matchIfMissing = true)
    SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> simpleContainerCustomizer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        simpleContainerCustomizer.ifUnique(factory::setContainerCustomizer);
        factory.setAfterReceivePostProcessors(message -> {
            Object header = message.getMessageProperties().getHeader(REQUEST_ID_HEADER);
            if(header != null) {
                MDC.put(REQUEST_ID_HEADER, header.toString());
            }
            return message;
        });
        return factory;
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper mapper){
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter(mapper);
        // 2.配置自动创建消息id，用于识别不同消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    /**
     * <h1>消息处理失败的重试策略</h1>
     * 本地重试失败后，消息投递到专门的失败交换机和失败消息队列：error.queue
     */
    @Bean
    @ConditionalOnClass(MessageRecoverer.class)
    @ConditionalOnMissingBean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate){
        // 消息处理失败后，发送到错误交换机：error.direct，RoutingKey默认是error.微服务名称
        return new RepublishMessageRecoverer(
                rabbitTemplate, ERROR_EXCHANGE, defaultErrorRoutingKey);
    }

    /**
     * rabbitmq发送工具
     *
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RabbitTemplate.class)
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate){
        return new RabbitMqHelper(rabbitTemplate);
    }

    /**
     * 专门接收处理失败的消息
     */
    @Bean
    public DirectExchange errorMessageExchange(){
        return new DirectExchange(ERROR_EXCHANGE);
    }

    @Bean
    public Queue errorQueue(){
        return new Queue(defaultErrorQueue, true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange){
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange).with(defaultErrorRoutingKey);
    }

    /**
     * <h1>P0 改造：DLX 死信队列声明</h1>
     * 业务失败重试仍无法消费的消息最终落到这里，由人工 / 监控兜底。
     * 名空间使用 {@link RabbitMqProperties} 统一管理。
     */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(rabbitMqProperties.getDlxExchange(), true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(rabbitMqProperties.currentDlxQueueName(), true);
    }

    @Bean
    public Binding dlxBinding(Queue dlxQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with(rabbitMqProperties.currentDlxRoutingKey());
    }

    /**
     * <h1>P0 改造：MySQL Outbox 存储</h1>
     *
     * <p>当 classpath 上存在 spring-jdbc 且上下文里存在 {@link JdbcTemplate} 时，
     * 装配基于 MySQL 的持久化 Outbox 存储。</p>
     *
     * <p><b>为什么单独放在嵌套配置类里：</b>该 {@code @Bean} 方法签名引用了
     * {@link JdbcTemplate}。如果它直接写在 {@link MqConfig} 上，那么在没有
     * spring-jdbc 的模块（例如纯 WebFlux 的网关）里，Spring 为求值
     * {@code @ConditionalOnBean} 会反射扫描 {@code MqConfig} 的全部方法，
     * 解析不到 {@code JdbcTemplate} 就抛 {@code NoClassDefFoundError}，
     * 导致整个应用启动失败。放到嵌套类上后，类级 {@code @ConditionalOnClass}
     * 走 ASM 元数据判定，spring-jdbc 缺失时这个类根本不会被加载。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcOutboxStoreConfiguration {

        @Bean
        @ConditionalOnBean(JdbcTemplate.class)
        @ConditionalOnMissingBean(LocalOutboxService.LocalOutboxStore.class)
        LocalOutboxService.LocalOutboxStore jdbcOutboxStore(JdbcTemplate jdbcTemplate) {
            return new JdbcOutboxStore(jdbcTemplate);
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        String appName = environment.getProperty("spring.application.name");
        this.defaultErrorRoutingKey = ERROR_KEY_PREFIX + appName;
        this.defaultErrorQueue = StringUtils.format(ERROR_QUEUE_TEMPLATE, appName);
        // P0 bugfix：把当前服务名注入到 Spring 容器内的 RabbitMqProperties 单例
        //（EnvironmentAware 回调先于 @Bean 方法执行，因此 dlxExchange()/dlxQueue() 拿到的 serviceName 一定已就绪）
        this.serviceName = (appName == null ? "unknown" : appName);
        this.rabbitMqProperties.setServiceName(serviceName);
    }
}
