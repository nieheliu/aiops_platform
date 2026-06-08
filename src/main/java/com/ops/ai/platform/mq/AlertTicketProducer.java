package com.ops.ai.platform.mq;

import com.ops.ai.platform.config.RabbitMqConfig;
import com.ops.ai.platform.dto.AlertTicketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlertTicketProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendCreateTicketMessage(Long alertId) {
        AlertTicketMessage message = new AlertTicketMessage();
        message.setAlertId(alertId);
        message.setTraceId(UUID.randomUUID().toString());
        message.setSendTime(LocalDateTime.now());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ALERT_EXCHANGE,
                RabbitMqConfig.ALERT_TICKET_ROUTING_KEY,
                message
        );
    }
}
