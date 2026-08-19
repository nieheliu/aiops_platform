package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.dto.KnowledgeArticleRequest;
import com.ops.ai.platform.dto.KnowledgeArticleResponse;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.entity.OpsKnowledgeAuditLog;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.service.KnowledgeArticleService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.SysUserService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge-articles")
public class KnowledgeArticleController {

    private static final Set<String> WORKFLOW_STATUSES = Set.of("DRAFT", "PENDING_REVIEW", "PUBLISHED", "ARCHIVED", "DEPRECATED");

    private final KnowledgeArticleService knowledgeArticleService;
    private final OpsKnowledgeService opsKnowledgeService;
    private final SysUserService sysUserService;
    private final TicketKnowledgeService ticketKnowledgeService;

    @PostMapping
    public KnowledgeArticleResponse create(@RequestBody KnowledgeArticleRequest request, HttpServletRequest httpRequest) {
        return knowledgeArticleService.create(request, currentUserId(httpRequest));
    }

    @PutMapping("/{id}")
    public KnowledgeArticleResponse update(@PathVariable Long id, @RequestBody KnowledgeArticleRequest request,
                                           HttpServletRequest httpRequest) {
        return knowledgeArticleService.update(id, request, currentUserId(httpRequest));
    }

    @GetMapping("/{id}")
    public KnowledgeArticleResponse get(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return knowledgeArticleService.get(id, userId, canManageAll(userId));
    }

    @GetMapping("/{id}/audit-logs")
    public List<OpsKnowledgeAuditLog> auditLogs(@PathVariable Long id) {
        return knowledgeArticleService.auditLogs(id);
    }

    @GetMapping("/workflow-list")
    public List<KnowledgeArticleResponse> workflowList(@RequestParam(required = false) String lifecycleStatus,
                                                       HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        boolean manageAll = canManageAll(userId);
        boolean admin = isAdmin(userId);
        LambdaQueryWrapper<OpsKnowledge> wrapper = new LambdaQueryWrapper<OpsKnowledge>()
                .orderByDesc(OpsKnowledge::getUpdateTime);
        if (!manageAll && !admin) {
            return List.of();
        }
        if (!admin) {
            wrapper.eq(OpsKnowledge::getCreatedBy, userId);
        }
        if (lifecycleStatus != null && WORKFLOW_STATUSES.contains(lifecycleStatus)) {
            wrapper.eq(OpsKnowledge::getLifecycleStatus, lifecycleStatus);
        } else {
            wrapper.in(OpsKnowledge::getLifecycleStatus, "DRAFT", "PENDING_REVIEW", "PUBLISHED", "ARCHIVED", "DEPRECATED");
        }
        return opsKnowledgeService.list(wrapper).stream().map(this::toBriefResponse).toList();
    }

    @PostMapping("/{id}/submit")
    public Boolean submit(@PathVariable Long id, HttpServletRequest httpRequest) {
        return knowledgeArticleService.submitReview(id, currentUserId(httpRequest));
    }

    @PostMapping("/{id}/publish")
    public Boolean publish(@PathVariable Long id, HttpServletRequest httpRequest) {
        return knowledgeArticleService.publish(id, currentUserId(httpRequest));
    }

    @PostMapping("/{id}/archive")
    public Boolean archive(@PathVariable Long id, HttpServletRequest httpRequest) {
        return knowledgeArticleService.archive(id, currentUserId(httpRequest));
    }

    @PostMapping("/{id}/deprecate")
    public Boolean deprecate(@PathVariable Long id, HttpServletRequest httpRequest) {
        return knowledgeArticleService.deprecate(id, currentUserId(httpRequest));
    }

    @PostMapping("/{id}/delete")
    public Boolean delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return knowledgeArticleService.deleteArticle(id, userId, canManageAll(userId));
    }

    private KnowledgeArticleResponse toBriefResponse(OpsKnowledge knowledge) {
        KnowledgeArticleResponse response = new KnowledgeArticleResponse();
        response.setId(knowledge.getId());
        response.setTitle(knowledge.getTitle());
        response.setLifecycleStatus(knowledge.getLifecycleStatus());
        response.setVersion(knowledge.getVersion());
        response.setComponent(knowledge.getComponent());
        response.setEntrySource(knowledge.getEntrySource());
        response.setDocumentId(ticketKnowledgeService.resolveDocumentId(knowledge));
        response.setUpdatedByName(resolveUsername(knowledge.getUpdatedBy()));
        response.setUpdateTime(knowledge.getUpdateTime());
        return response;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "-";
        }
        var user = sysUserService.getById(userId);
        return user == null ? ("用户" + userId) : user.getUsername();
    }

    private boolean canManageAll(Long userId) {
        return isAdmin(userId) || hasOpsRole(userId);
    }

    private boolean isAdmin(Long userId) {
        return sysUserService.getUserRoles(userId).stream().map(SysRole::getRoleCode).anyMatch("ADMIN"::equals);
    }

    private boolean hasOpsRole(Long userId) {
        return sysUserService.getUserRoles(userId).stream().map(SysRole::getRoleCode).anyMatch("OPS"::equals);
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
