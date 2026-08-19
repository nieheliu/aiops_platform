package com.ops.ai.platform.controller;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.service.IService;

import com.ops.ai.platform.common.BaseController;

import com.ops.ai.platform.dto.DiagnoseRequest;

import com.ops.ai.platform.dto.TicketAssignRequest;

import com.ops.ai.platform.dto.TicketResolveRequest;

import com.ops.ai.platform.entity.AiDiagnosis;

import com.ops.ai.platform.entity.OpsTicket;

import com.ops.ai.platform.service.AiDiagnosisService;

import com.ops.ai.platform.service.AiDiagnosisWorkflowService;

import com.ops.ai.platform.service.OpsTicketService;

import com.ops.ai.platform.service.TicketKnowledgeService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;



import java.io.Serializable;

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



    @Override
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Serializable id) {
        throw new UnsupportedOperationException("工单不支持物理删除，请使用关闭工单流程");

    }



    @PostMapping("/{id}/start")

    public Boolean start(@PathVariable Long id, HttpServletRequest request) {

        return ticketKnowledgeService.startTicket(id, currentUserId(request));

    }



    @PutMapping("/{id}/assign")

    public Boolean assign(@PathVariable Long id, @RequestBody TicketAssignRequest body, HttpServletRequest request) {

        return ticketKnowledgeService.assignHandler(id, body == null ? null : body.getHandlerUserId(), currentUserId(request));

    }



    @PostMapping("/{id}/resolve")

    public Boolean resolve(@PathVariable Long id, @RequestBody TicketResolveRequest requestBody, HttpServletRequest request) {

        return ticketKnowledgeService.resolveTicket(id, requestBody, currentUserId(request));

    }



    @PostMapping("/{id}/close")

    public Boolean close(@PathVariable Long id, HttpServletRequest request) {

        return ticketKnowledgeService.closeTicket(id, currentUserId(request));

    }



    @PostMapping("/{id}/diagnose")

    public AiDiagnosis diagnose(@PathVariable Long id, @RequestBody DiagnoseRequest request, HttpServletRequest httpRequest) {

        return aiDiagnosisWorkflowService.diagnoseTicket(id, request == null ? null : request.getModelId(), currentUserId(httpRequest));

    }



    @GetMapping("/{id}/diagnoses")

    public List<AiDiagnosis> diagnoses(@PathVariable Long id) {

        return aiDiagnosisService.list(new LambdaQueryWrapper<AiDiagnosis>()

                .eq(AiDiagnosis::getTicketId, id)

                .orderByDesc(AiDiagnosis::getCreateTime));

    }



    private Long currentUserId(HttpServletRequest request) {

        Object userId = request.getAttribute("currentUserId");

        if (userId instanceof Long longValue) {

            return longValue;

        }

        if (userId instanceof Number number) {

            return number.longValue();

        }

        return null;

    }

}


