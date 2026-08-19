package com.ops.ai.platform.controller;



import com.ops.ai.platform.dto.KnowledgeFacetsResponse;

import com.ops.ai.platform.dto.KnowledgeSearchQuery;

import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;

import com.ops.ai.platform.entity.SysRole;

import com.ops.ai.platform.service.SysUserService;

import com.ops.ai.platform.service.TicketKnowledgeService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;



@RestController

@RequiredArgsConstructor

@RequestMapping("/ticket-knowledge")

public class TicketKnowledgeController {



    private final TicketKnowledgeService ticketKnowledgeService;

    private final SysUserService sysUserService;



    @GetMapping("/search")

    public TicketKnowledgeSearchResponse search(@RequestParam(defaultValue = "") String keyword,

                                                @RequestParam(defaultValue = "1") int page,

                                                @RequestParam(defaultValue = "10") int size,

                                                @RequestParam(required = false) String sourceType,

                                                @RequestParam(required = false) String component,

                                                @RequestParam(required = false) String aiModel,

                                                @RequestParam(required = false) String lifecycleStatus,

                                                @RequestParam(required = false) String dateFrom,

                                                @RequestParam(required = false) String dateTo,

                                                HttpServletRequest request) {

        KnowledgeSearchQuery query = buildQuery(keyword, page, size, sourceType, component, aiModel,

                lifecycleStatus, dateFrom, dateTo, request);

        return ticketKnowledgeService.search(query);

    }



    @GetMapping("/facets")

    public KnowledgeFacetsResponse facets(@RequestParam(defaultValue = "") String keyword,

                                          @RequestParam(required = false) String sourceType,

                                          @RequestParam(required = false) String component,

                                          @RequestParam(required = false) String aiModel,

                                          @RequestParam(required = false) String lifecycleStatus,

                                          @RequestParam(required = false) String dateFrom,

                                          @RequestParam(required = false) String dateTo,

                                          HttpServletRequest request) {

        KnowledgeSearchQuery query = buildQuery(keyword, 1, 10, sourceType, component, aiModel,

                lifecycleStatus, dateFrom, dateTo, request);

        return ticketKnowledgeService.facets(query);

    }



    @DeleteMapping("/{documentId}")

    public Boolean delete(@PathVariable String documentId, HttpServletRequest request) {

        Long userId = currentUserId(request);

        if (!canManageKnowledge(userId)) {

            throw new IllegalStateException("无权删除知识");

        }

        return ticketKnowledgeService.deleteDocument(documentId);

    }



    private KnowledgeSearchQuery buildQuery(String keyword, int page, int size, String sourceType, String component,

                                              String aiModel, String lifecycleStatus, String dateFrom, String dateTo,

                                              HttpServletRequest request) {

        KnowledgeSearchQuery query = new KnowledgeSearchQuery();

        query.setKeyword(keyword);

        query.setPage(page);

        query.setSize(size);

        query.setSourceType(sourceType);

        query.setComponent(component);

        query.setAiModel(aiModel);

        query.setLifecycleStatus(lifecycleStatus);

        query.setDateFrom(dateFrom);

        query.setDateTo(dateTo);

        query.setIncludeAllStatuses(canManageKnowledge(currentUserId(request)));

        return query;

    }



    private boolean canManageKnowledge(Long userId) {

        if (userId == null) {

            return false;

        }

        return sysUserService.getUserRoles(userId).stream()

                .map(SysRole::getRoleCode)

                .anyMatch(code -> "ADMIN".equals(code) || "OPS".equals(code));

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


