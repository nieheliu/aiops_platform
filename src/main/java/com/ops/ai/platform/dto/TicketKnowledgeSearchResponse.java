package com.ops.ai.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TicketKnowledgeSearchResponse {

    private List<Record> records;

    private long total;

    private long page;

    private long size;

    @Data
    public static class Record {
        private Long ticketId;
        private Long alertId;
        private String title;
        private String description;
        private String aiRootCause;
        private String aiSuggestedFix;
        private String experienceSummary;
        private LocalDateTime resolvedAt;
        private String titleHighlight;
        private String descriptionHighlight;
        private String aiRootCauseHighlight;
        private String aiSuggestedFixHighlight;
        private String experienceSummaryHighlight;
    }
}
