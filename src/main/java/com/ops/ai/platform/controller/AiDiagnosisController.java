package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai-diagnoses")
public class AiDiagnosisController extends BaseController<AiDiagnosis> {

    private final AiDiagnosisService aiDiagnosisService;

    private final AiDiagnosisWorkflowService aiDiagnosisWorkflowService;

    @Override
    protected IService<AiDiagnosis> service() {
        return aiDiagnosisService;
    }

    @PostMapping("/{id}/to-knowledge")
    public OpsKnowledge toKnowledge(@PathVariable Long id) {
        return aiDiagnosisWorkflowService.convertDiagnosisToKnowledge(id);
    }
}
