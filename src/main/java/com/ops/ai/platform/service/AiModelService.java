package com.ops.ai.platform.service;

import com.ops.ai.platform.config.AiOpsModelProperties;
import com.ops.ai.platform.dto.AiModelOption;
import com.ops.ai.platform.entity.AiDiagnosis;

import java.util.List;

public interface AiModelService {

    String getDefaultModelId();

    AiOpsModelProperties.ModelDefinition requireModel(String modelId);

    List<AiModelOption> listAll();

    List<AiModelOption> listAvailable(Long alertId, Long ticketId);

    void ensureModelNotUsed(Long alertId, Long ticketId, String modelId);

    AiDiagnosis findExistingDiagnosis(Long alertId, Long ticketId, String modelId);
}
