package com.ops.ai.platform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    public static final String ALERT_EXCHANGE = "ops.alert.exchange";
    public static final String ALERT_DLX = "ops.alert.dlx";
    public static final String ALERT_TICKET_QUEUE = "ops.alert.ticket.queue";
    public static final String ALERT_TICKET_DLQ = "ops.alert.ticket.dlq";
    public static final String ALERT_TICKET_ROUTING_KEY = "alert.ticket.create";
    public static final String ALERT_TICKET_DEAD_ROUTING_KEY = "alert.ticket.dead";

    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange alertDlx() {
        return new DirectExchange(ALERT_DLX, true, false);
    }

    @Bean
    public Queue alertTicketQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", ALERT_DLX);
        arguments.put("x-dead-letter-routing-key", ALERT_TICKET_DEAD_ROUTING_KEY);
        return new Queue(ALERT_TICKET_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Queue alertTicketDlq() {
        return new Queue(ALERT_TICKET_DLQ, true);
    }

    @Bean
    public Binding alertTicketBinding(Queue alertTicketQueue, DirectExchange alertExchange) {
        return BindingBuilder.bind(alertTicketQueue)
                .to(alertExchange)
                .with(ALERT_TICKET_ROUTING_KEY);
    }

    @Bean
    public Binding alertTicketDlqBinding(Queue alertTicketDlq, DirectExchange alertDlx) {
        return BindingBuilder.bind(alertTicketDlq)
                .to(alertDlx)
                .with(ALERT_TICKET_DEAD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setPrefetchCount(1);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        return factory;
    }
}
