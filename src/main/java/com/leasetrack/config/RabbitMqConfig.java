package com.leasetrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMqConfig {

    public static final String NOTICE_EVENTS_EXCHANGE = "lease-track.notice-events";
    public static final String NOTICE_EVENTS_QUEUE = "lease-track.notice-events";
    public static final String NOTICE_EVENTS_ROUTING_KEY = "notice.events";

    @Bean
    public DirectExchange noticeEventsExchange() {
        return new DirectExchange(NOTICE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue noticeEventsQueue() {
        return new Queue(NOTICE_EVENTS_QUEUE, true);
    }

    @Bean
    public Binding noticeEventsBinding(Queue noticeEventsQueue, DirectExchange noticeEventsExchange) {
        return BindingBuilder.bind(noticeEventsQueue)
                .to(noticeEventsExchange)
                .with(NOTICE_EVENTS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
