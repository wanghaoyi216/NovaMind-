package com.novamind.trade.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.novamind.common.constants.MqConstants.Exchange.TRADE_DELAY_EXCHANGE;
import static com.novamind.common.constants.MqConstants.Key.ORDER_DELAY_KEY;

@Configuration
public class TradeDelayMqConfig {

    public static final String TRADE_DELAY_QUEUE = "trade.delay.order.query";
    public static final String TRADE_DELAY_QUEUE_ROUTING_KEY = "delay.order.query.ttl";
    public static final String TRADE_ORDER_QUERY_QUEUE = "trade.order.query.queue";

    @Bean
    public TopicExchange tradeDelayExchange() {
        return new TopicExchange(TRADE_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue tradeDelayOrderQueryQueue() {
        return QueueBuilder.durable(TRADE_DELAY_QUEUE)
                .deadLetterExchange(TRADE_DELAY_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DELAY_KEY)
                .build();
    }

    @Bean
    public Binding tradeDelayOrderQueryBinding(
            Queue tradeDelayOrderQueryQueue,
            TopicExchange tradeDelayExchange) {
        return BindingBuilder.bind(tradeDelayOrderQueryQueue)
                .to(tradeDelayExchange)
                .with(TRADE_DELAY_QUEUE_ROUTING_KEY);
    }

    @Bean
    public Queue tradeOrderQueryQueue() {
        return QueueBuilder.durable(TRADE_ORDER_QUERY_QUEUE).build();
    }

    @Bean
    public Binding tradeOrderQueryBinding(
            Queue tradeOrderQueryQueue,
            TopicExchange tradeDelayExchange) {
        return BindingBuilder.bind(tradeOrderQueryQueue)
                .to(tradeDelayExchange)
                .with(ORDER_DELAY_KEY);
    }
}
