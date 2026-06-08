package com.ops.ai.platform.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AlertWebhookRequest {

    private String receiver;

    private String status;

    private List<AlertWebhookItem> alerts;

    private Map<String, String> groupLabels;

    private Map<String, String> commonLabels;

    private Map<String, String> commonAnnotations;

    private String externalURL;

    private String version;

    private String groupKey;

    private Integer truncatedAlerts;
}
