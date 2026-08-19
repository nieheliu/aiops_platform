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
        private Long diagnosisId;
        private String title;
        private String description;
        private String aiRootCause;
        private String aiSuggestedFix;
        private String experienceSummary;
        private LocalDateTime resolvedAt;
        private LocalDateTime indexedAt;
        private String titleHighlight;
        private String descriptionHighlight;
        private String aiRootCauseHighlight;
        private String aiSuggestedFixHighlight;
        private String experienceSummaryHighlight;
        private String sourceType;
        private String aiModel;
        private String documentId;
        private Long knowledgeId;
        private String lifecycleStatus;
        private Integer version;
        private String component;
        private String createdByName;
        private String updatedByName;
        private String contentMd;
    }
}
