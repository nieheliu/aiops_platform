package com.ops.ai.platform.dto;

import lombok.Data;

@Data
public class KnowledgeArticleRequest {

    private String title;

    private String contentMd;

    private String component;

    private String tags;
}
