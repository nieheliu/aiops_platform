package com.ops.ai.platform.service;

import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsKnowledge;

public interface AiDiagnosisWorkflowService {

    AiDiagnosis diagnoseAlert(Long alertId);

    AiDiagnosis diagnoseTicket(Long ticketId);

    AiDiagnosis diagnoseAlertTicket(Long alertId, Long ticketId);

    OpsKnowledge convertDiagnosisToKnowledge(Long diagnosisId);
}
