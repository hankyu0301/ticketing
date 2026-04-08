package com.ticketing.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${ticketing.payment.rabbitmq.exchange:ticketing.payment}")
    private String exchangeName;

    @Value("${ticketing.payment.rabbitmq.queue:ticketing.payment.requested}")
    private String queueName;

    @Value("${ticketing.payment.rabbitmq.routing-key:payment.requested}")
    private String routingKey;

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue paymentQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding paymentBinding(DirectExchange paymentExchange, Queue paymentQueue) {
        return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(routingKey);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
