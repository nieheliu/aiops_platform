package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.common.KnowledgeEntrySource;
import com.ops.ai.platform.common.KnowledgeLifecycle;
import com.ops.ai.platform.dto.KnowledgeArticleRequest;
import com.ops.ai.platform.dto.KnowledgeArticleResponse;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.entity.OpsKnowledgeAuditLog;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.service.DashboardCacheService;
import com.ops.ai.platform.service.KnowledgeArticleService;
import com.ops.ai.platform.service.OpsKnowledgeAuditLogService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.SysUserService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private static final Set<String> EDITABLE_STATUSES = Set.of(
            KnowledgeLifecycle.DRAFT,
            KnowledgeLifecycle.PENDING_REVIEW
    );

    private final OpsKnowledgeService opsKnowledgeService;
    private final OpsKnowledgeAuditLogService auditLogService;
    private final SysUserService sysUserService;
    private final TicketKnowledgeService ticketKnowledgeService;
    private final DashboardCacheService dashboardCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleResponse create(KnowledgeArticleRequest request, Long operatorUserId) {
        validateArticleRequest(request);
        OpsKnowledge knowledge = new OpsKnowledge();
        knowledge.setTitle(request.getTitle().trim());
        knowledge.setContentMd(request.getContentMd().trim());
        knowledge.setComponent(normalizeComponent(request.getComponent()));
        knowledge.setTags(StringUtils.hasText(request.getTags()) ? request.getTags().trim() : "[\"手动录入\"]");
        knowledge.setLifecycleStatus(KnowledgeLifecycle.DRAFT);
        knowledge.setVersion(1);
        knowledge.setEntrySource(KnowledgeEntrySource.MANUAL_IMPORT);
        knowledge.setSyncEsStatus(0);
        knowledge.setCreatedBy(operatorUserId);
        knowledge.setUpdatedBy(operatorUserId);
        opsKnowledgeService.save(knowledge);
        saveAudit(knowledge.getId(), "CREATE", operatorUserId, null, KnowledgeLifecycle.DRAFT, knowledge.getVersion(), "创建知识草稿");
        return toResponse(knowledge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticleResponse update(Long id, KnowledgeArticleRequest request, Long operatorUserId) {
        OpsKnowledge knowledge = requireKnowledge(id);
        assertEditable(knowledge, operatorUserId, true);
        validateArticleRequest(request);
        knowledge.setTitle(request.getTitle().trim());
        knowledge.setContentMd(request.getContentMd().trim());
        knowledge.setComponent(normalizeComponent(request.getComponent()));
        if (StringUtils.hasText(request.getTags())) {
            knowledge.setTags(request.getTags().trim());
        }
        knowledge.setUpdatedBy(operatorUserId);
        if (KnowledgeLifecycle.DRAFT.equals(knowledge.getLifecycleStatus())) {
            knowledge.setVersion((knowledge.getVersion() == null ? 1 : knowledge.getVersion()) + 1);
        }
        opsKnowledgeService.updateById(knowledge);
        saveAudit(knowledge.getId(), "UPDATE", operatorUserId, knowledge.getLifecycleStatus(), knowledge.getLifecycleStatus(),
                knowledge.getVersion(), "更新知识内容");
        return toResponse(knowledge);
    }

    @Override
    public KnowledgeArticleResponse get(Long id, Long operatorUserId, boolean canManageAll) {
        OpsKnowledge knowledge = requireKnowledge(id);
        if (!canView(knowledge, operatorUserId, canManageAll)) {
            throw new IllegalStateException("无权查看该知识");
        }
        return toResponse(knowledge);
    }

    @Override
    public List<OpsKnowledgeAuditLog> auditLogs(Long id) {
        requireKnowledge(id);
        return auditLogService.list(new LambdaQueryWrapper<OpsKnowledgeAuditLog>()
                .eq(OpsKnowledgeAuditLog::getKnowledgeId, id)
                .orderByDesc(OpsKnowledgeAuditLog::getOperateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitReview(Long id, Long operatorUserId) {
        OpsKnowledge knowledge = requireKnowledge(id);
        assertEditable(knowledge, operatorUserId, true);
        String from = knowledge.getLifecycleStatus();
        knowledge.setLifecycleStatus(KnowledgeLifecycle.PENDING_REVIEW);
        knowledge.setUpdatedBy(operatorUserId);
        opsKnowledgeService.updateById(knowledge);
        saveAudit(id, "SUBMIT", operatorUserId, from, KnowledgeLifecycle.PENDING_REVIEW, knowledge.getVersion(), "提交审核");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean publish(Long id, Long operatorUserId) {
        requireAdmin(operatorUserId);
        OpsKnowledge knowledge = requireKnowledge(id);
        if (!KnowledgeLifecycle.DRAFT.equals(knowledge.getLifecycleStatus())
                && !KnowledgeLifecycle.PENDING_REVIEW.equals(knowledge.getLifecycleStatus())
                && !KnowledgeLifecycle.ARCHIVED.equals(knowledge.getLifecycleStatus())) {
            throw new IllegalStateException("当前状态不可发布");
        }
        String from = knowledge.getLifecycleStatus();
        knowledge.setLifecycleStatus(KnowledgeLifecycle.PUBLISHED);
        knowledge.setVersion((knowledge.getVersion() == null ? 1 : knowledge.getVersion()) + 1);
        knowledge.setReviewedBy(operatorUserId);
        knowledge.setReviewedAt(LocalDateTime.now());
        knowledge.setUpdatedBy(operatorUserId);
        knowledge.setSyncEsStatus(1);
        opsKnowledgeService.updateById(knowledge);
        ticketKnowledgeService.syncKnowledgeDocument(knowledge);
        saveAudit(id, "PUBLISH", operatorUserId, from, KnowledgeLifecycle.PUBLISHED, knowledge.getVersion(), "审核通过并发布");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean archive(Long id, Long operatorUserId) {
        requireAdmin(operatorUserId);
        OpsKnowledge knowledge = requireKnowledge(id);
        if (!KnowledgeLifecycle.PUBLISHED.equals(knowledge.getLifecycleStatus())) {
            throw new IllegalStateException("只有已发布知识可以归档");
        }
        String from = knowledge.getLifecycleStatus();
        knowledge.setLifecycleStatus(KnowledgeLifecycle.ARCHIVED);
        knowledge.setUpdatedBy(operatorUserId);
        opsKnowledgeService.updateById(knowledge);
        ticketKnowledgeService.syncKnowledgeDocument(knowledge);
        saveAudit(id, "ARCHIVE", operatorUserId, from, KnowledgeLifecycle.ARCHIVED, knowledge.getVersion(), "归档知识");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deprecate(Long id, Long operatorUserId) {
        requireAdmin(operatorUserId);
        OpsKnowledge knowledge = requireKnowledge(id);
        String from = knowledge.getLifecycleStatus();
        knowledge.setLifecycleStatus(KnowledgeLifecycle.DEPRECATED);
        knowledge.setUpdatedBy(operatorUserId);
        knowledge.setSyncEsStatus(0);
        opsKnowledgeService.updateById(knowledge);
        ticketKnowledgeService.removeElasticsearchDocument(ticketKnowledgeService.resolveDocumentId(knowledge));
        saveAudit(id, "DEPRECATE", operatorUserId, from, KnowledgeLifecycle.DEPRECATED, knowledge.getVersion(), "标记废弃并移出检索");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteArticle(Long id, Long operatorUserId, boolean canManageAll) {
        OpsKnowledge knowledge = requireKnowledge(id);
        boolean isAdmin = isAdmin(operatorUserId);
        if (KnowledgeLifecycle.PUBLISHED.equals(knowledge.getLifecycleStatus())
                || KnowledgeLifecycle.ARCHIVED.equals(knowledge.getLifecycleStatus())) {
            if (!isAdmin) {
                throw new IllegalStateException("已发布/已归档知识仅管理员可删除");
            }
        } else if (!canManageAll && !operatorUserId.equals(knowledge.getCreatedBy()) && !isAdmin) {
            throw new IllegalStateException("无权删除该知识");
        }
        saveAudit(id, "DELETE", operatorUserId, knowledge.getLifecycleStatus(), null, knowledge.getVersion(), "删除知识");
        ticketKnowledgeService.deleteDocument(ticketKnowledgeService.resolveDocumentId(knowledge));
        dashboardCacheService.evictSummary();
        return true;
    }

    private OpsKnowledge requireKnowledge(Long id) {
        OpsKnowledge knowledge = opsKnowledgeService.getById(id);
        if (knowledge == null) {
            throw new IllegalArgumentException("知识不存在");
        }
        return knowledge;
    }

    private void validateArticleRequest(KnowledgeArticleRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getContentMd())) {
            throw new IllegalArgumentException("标题和内容不能为空");
        }
    }

    private void assertEditable(OpsKnowledge knowledge, Long operatorUserId, boolean canManageAll) {
        if (!EDITABLE_STATUSES.contains(knowledge.getLifecycleStatus())) {
            throw new IllegalStateException("当前状态不可编辑");
        }
        if (!canManageAll && !operatorUserId.equals(knowledge.getCreatedBy()) && !isAdmin(operatorUserId)) {
            throw new IllegalStateException("仅创建者或管理员可编辑该知识");
        }
    }

    private boolean canView(OpsKnowledge knowledge, Long operatorUserId, boolean canManageAll) {
        if (canManageAll || isAdmin(operatorUserId)) {
            return true;
        }
        if (operatorUserId != null && operatorUserId.equals(knowledge.getCreatedBy())) {
            return true;
        }
        return KnowledgeLifecycle.PUBLISHED.equals(knowledge.getLifecycleStatus())
                || KnowledgeLifecycle.ARCHIVED.equals(knowledge.getLifecycleStatus());
    }

    private void requireAdmin(Long operatorUserId) {
        if (!isAdmin(operatorUserId)) {
            throw new IllegalStateException("仅管理员可执行审核发布、归档和废弃操作");
        }
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return sysUserService.getUserRoles(userId).stream()
                .map(SysRole::getRoleCode)
                .anyMatch("ADMIN"::equals);
    }

    private String normalizeComponent(String component) {
        return StringUtils.hasText(component) ? component.trim().toLowerCase() : "other";
    }

    private void saveAudit(Long knowledgeId, String action, Long operatorUserId, String fromStatus, String toStatus,
                           Integer version, String remark) {
        OpsKnowledgeAuditLog auditLog = new OpsKnowledgeAuditLog();
        auditLog.setKnowledgeId(knowledgeId);
        auditLog.setAction(action);
        auditLog.setOperatorId(operatorUserId);
        auditLog.setOperatorName(resolveUsername(operatorUserId));
        auditLog.setFromStatus(fromStatus);
        auditLog.setToStatus(toStatus);
        auditLog.setVersion(version);
        auditLog.setRemark(remark);
        auditLog.setOperateTime(LocalDateTime.now());
        auditLogService.save(auditLog);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = sysUserService.getById(userId);
        return user == null ? ("用户" + userId) : user.getUsername();
    }

    private KnowledgeArticleResponse toResponse(OpsKnowledge knowledge) {
        KnowledgeArticleResponse response = new KnowledgeArticleResponse();
        response.setId(knowledge.getId());
        response.setTitle(knowledge.getTitle());
        response.setContentMd(knowledge.getContentMd());
        response.setComponent(knowledge.getComponent());
        response.setTags(knowledge.getTags());
        response.setLifecycleStatus(knowledge.getLifecycleStatus());
        response.setVersion(knowledge.getVersion());
        response.setEntrySource(knowledge.getEntrySource());
        response.setSourceAlertId(knowledge.getSourceAlertId());
        response.setSourceTicketId(knowledge.getSourceTicketId());
        response.setSourceDiagnosisId(knowledge.getSourceDiagnosisId());
        response.setDocumentId(ticketKnowledgeService.resolveDocumentId(knowledge));
        response.setCreatedBy(knowledge.getCreatedBy());
        response.setCreatedByName(resolveUsername(knowledge.getCreatedBy()));
        response.setUpdatedBy(knowledge.getUpdatedBy());
        response.setUpdatedByName(resolveUsername(knowledge.getUpdatedBy()));
        response.setReviewedBy(knowledge.getReviewedBy());
        response.setReviewedByName(resolveUsername(knowledge.getReviewedBy()));
        response.setReviewedAt(knowledge.getReviewedAt());
        response.setCreateTime(knowledge.getCreateTime());
        response.setUpdateTime(knowledge.getUpdateTime());
        return response;
    }
}
