package com.ops.ai.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertTicketMessage {

    private Long alertId;

    private String traceId;

    private LocalDateTime sendTime;
}
