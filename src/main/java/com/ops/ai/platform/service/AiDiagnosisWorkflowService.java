package com.ops.ai.platform.service;

import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsKnowledge;

public interface AiDiagnosisWorkflowService {

    AiDiagnosis diagnoseAlert(Long alertId, String modelId);

    AiDiagnosis diagnoseTicket(Long ticketId, String modelId, Long operatorUserId);

    AiDiagnosis diagnoseAlertTicket(Long alertId, Long ticketId, String modelId);

    OpsKnowledge convertDiagnosisToKnowledge(Long diagnosisId);
}
