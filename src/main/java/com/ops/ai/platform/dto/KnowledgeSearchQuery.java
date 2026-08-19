package com.ops.ai.platform.dto;

import lombok.Data;

@Data
public class KnowledgeSearchQuery {

    private String keyword = "";

    private int page = 1;

    private int size = 10;

    private String sourceType;

    private String component;

    private String aiModel;

    private String lifecycleStatus;

    private String dateFrom;

    private String dateTo;

    private boolean includeAllStatuses;
}
