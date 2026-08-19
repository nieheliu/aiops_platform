package com.ops.ai.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeArticleResponse {

    private Long id;

    private String title;

    private String contentMd;

    private String component;

    private String tags;

    private String lifecycleStatus;

    private Integer version;

    private String entrySource;

    private Long sourceAlertId;

    private Long sourceTicketId;

    private Long sourceDiagnosisId;

    private String documentId;

    private Long createdBy;

    private String createdByName;

    private Long updatedBy;

    private String updatedByName;

    private Long reviewedBy;

    private String reviewedByName;

    private LocalDateTime reviewedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
