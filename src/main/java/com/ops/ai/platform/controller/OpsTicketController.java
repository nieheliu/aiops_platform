package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.dto.TicketResolveRequest;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import com.ops.ai.platform.service.OpsTicketService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops-tickets")
public class OpsTicketController extends BaseController<OpsTicket> {

    private final OpsTicketService opsTicketService;

    private final TicketKnowledgeService ticketKnowledgeService;

    private final AiDiagnosisService aiDiagnosisService;

    private final AiDiagnosisWorkflowService aiDiagnosisWorkflowService;

    @Override
    protected IService<OpsTicket> service() {
        return opsTicketService;
    }

    @PostMapping("/{id}/start")
    public Boolean start(@PathVariable Long id) {
        return ticketKnowledgeService.startTicket(id);
    }

    @PostMapping("/{id}/resolve")
    public Boolean resolve(@PathVariable Long id, @RequestBody TicketResolveRequest request) {
        return ticketKnowledgeService.resolveTicket(id, request);
    }

    @PostMapping("/{id}/close")
    public Boolean close(@PathVariable Long id) {
        return ticketKnowledgeService.closeTicket(id);
    }

    @PostMapping("/{id}/diagnose")
    public AiDiagnosis diagnose(@PathVariable Long id) {
        return aiDiagnosisWorkflowService.diagnoseTicket(id);
    }

    @GetMapping("/{id}/diagnoses")
    public List<AiDiagnosis> diagnoses(@PathVariable Long id) {
        return aiDiagnosisService.list(new LambdaQueryWrapper<AiDiagnosis>()
                .eq(AiDiagnosis::getTicketId, id)
                .orderByDesc(AiDiagnosis::getCreateTime));
    }
}
