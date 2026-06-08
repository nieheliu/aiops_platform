package com.ops.ai.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.ai.platform.dto.AlertWebhookItem;
import com.ops.ai.platform.dto.AlertWebhookRequest;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.mq.AlertTicketProducer;
import com.ops.ai.platform.service.OpsAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/alerts")
public class AlertWebhookController {

    private static final String FIRING_STATUS = "firing";

    private final OpsAlertService opsAlertService;

    private final AlertTicketProducer alertTicketProducer;

    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public Map<String, Object> receiveWebhook(@RequestBody AlertWebhookRequest request) {
        int received = request == null || request.getAlerts() == null ? 0 : request.getAlerts().size();
        List<OpsAlert> alerts = convertToOpsAlerts(request);

        if (!alerts.isEmpty()) {
            opsAlertService.saveBatch(alerts);
            alerts.stream()
                    .map(OpsAlert::getId)
                    .filter(id -> id != null)
                    .forEach(alertTicketProducer::sendCreateTicketMessage);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("received", received);
        result.put("saved", alerts.size());
        return result;
    }

    private List<OpsAlert> convertToOpsAlerts(AlertWebhookRequest request) {
        List<OpsAlert> opsAlerts = new ArrayList<>();
        if (request == null || CollectionUtils.isEmpty(request.getAlerts())) {
            return opsAlerts;
        }

        for (AlertWebhookItem webhookItem : request.getAlerts()) {
            if (webhookItem == null || !FIRING_STATUS.equalsIgnoreCase(webhookItem.getStatus())) {
                continue;
            }

            Map<String, String> labels = webhookItem.getLabels();
            OpsAlert opsAlert = new OpsAlert();
            opsAlert.setAlertName(getMapValue(labels, "alertname", "unknown-alert"));
            opsAlert.setSeverity(mapSeverity(getMapValue(labels, "severity", null)));
            opsAlert.setInstanceIp(getMapValue(labels, "instance", "unknown-instance"));
            opsAlert.setRawPayload(toJson(webhookItem));
            opsAlert.setTriggerTime(parseTriggerTime(webhookItem.getStartsAt()));
            opsAlerts.add(opsAlert);
        }

        return opsAlerts;
    }

    private String getMapValue(Map<String, String> map, String key, String defaultValue) {
        if (CollectionUtils.isEmpty(map)) {
            return defaultValue;
        }
        String value = map.get(key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer mapSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return 2;
        }
        return switch (severity.toLowerCase()) {
            case "critical" -> 4;
            case "warning" -> 3;
            case "info" -> 1;
            default -> 2;
        };
    }

    private LocalDateTime parseTriggerTime(String startsAt) {
        if (!StringUtils.hasText(startsAt)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(startsAt), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
