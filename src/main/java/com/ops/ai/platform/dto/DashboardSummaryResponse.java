package com.ops.ai.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardSummaryResponse {

    private Long todayAlertCount = 0L;

    private Long pendingTicketCount = 0L;

    private Long todayDiagnosisCount = 0L;

    private Long knowledgeCount = 0L;

    private List<StatItem> ticketStatusStats = new ArrayList<>();

    private List<StatItem> alertSeverityStats = new ArrayList<>();

    private List<StatItem> alertTrend = new ArrayList<>();

    private Boolean cacheHit = false;

    private LocalDateTime generatedAt;

    private Long expireSeconds = 300L;
}
