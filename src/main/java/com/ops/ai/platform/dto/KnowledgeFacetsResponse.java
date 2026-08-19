package com.ops.ai.platform.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class KnowledgeFacetsResponse {

    private Map<String, Long> sourceTypes = new LinkedHashMap<>();

    private Map<String, Long> components = new LinkedHashMap<>();

    private Map<String, Long> aiModels = new LinkedHashMap<>();

    private Map<String, Long> lifecycleStatuses = new LinkedHashMap<>();
}
