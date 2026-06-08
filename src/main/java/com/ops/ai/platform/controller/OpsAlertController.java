package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import com.ops.ai.platform.service.AlertTicketService;
import com.ops.ai.platform.service.OpsAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops-alerts")
public class OpsAlertController extends BaseController<OpsAlert> {

    private final OpsAlertService opsAlertService;

    private final AlertTicketService alertTicketService;

    private final AiDiagnosisService aiDiagnosisService;

    private final AiDiagnosisWorkflowService aiDiagnosisWorkflowService;

    @Override
    protected IService<OpsAlert> service() {
        return opsAlertService;
    }

    @PostMapping("/{id}/create-ticket")
    public OpsTicket createTicket(@PathVariable Long id) {
        return alertTicketService.createTicketFromAlert(id);
    }

    @PostMapping("/{id}/diagnose")
    public AiDiagnosis diagnose(@PathVariable Long id) {
        return aiDiagnosisWorkflowService.diagnoseAlert(id);
    }

    @GetMapping("/{id}/diagnoses")
    public List<AiDiagnosis> diagnoses(@PathVariable Long id) {
        return aiDiagnosisService.list(new LambdaQueryWrapper<AiDiagnosis>()
                .eq(AiDiagnosis::getAlertId, id)
                .orderByDesc(AiDiagnosis::getCreateTime));
    }
}
