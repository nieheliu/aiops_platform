package com.ops.ai.platform.mq;

import com.ops.ai.platform.config.RabbitMqConfig;
import com.ops.ai.platform.dto.AlertTicketMessage;
import com.ops.ai.platform.service.AlertWorkflowService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTicketConsumer {

    private final AlertWorkflowService alertWorkflowService;

    @RabbitListener(queues = RabbitMqConfig.ALERT_TICKET_QUEUE)
    public void handleCreateTicketMessage(AlertTicketMessage alertTicketMessage, Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (alertTicketMessage == null || alertTicketMessage.getAlertId() == null) {
                log.warn("Invalid alert ticket message: {}", alertTicketMessage);
                channel.basicReject(deliveryTag, false);
                return;
            }

            alertWorkflowService.createTicketFromAlert(alertTicketMessage.getAlertId());
            channel.basicAck(deliveryTag, false);
            log.info("Alert ticket message consumed, alertId={}, traceId={}",
                    alertTicketMessage.getAlertId(), alertTicketMessage.getTraceId());
        } catch (Exception e) {
            log.error("Failed to consume alert ticket message: {}", alertTicketMessage, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
