package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.KnowledgeArticleRequest;
import com.ops.ai.platform.dto.KnowledgeArticleResponse;
import com.ops.ai.platform.entity.OpsKnowledgeAuditLog;

import java.util.List;

public interface KnowledgeArticleService {

    KnowledgeArticleResponse create(KnowledgeArticleRequest request, Long operatorUserId);

    KnowledgeArticleResponse update(Long id, KnowledgeArticleRequest request, Long operatorUserId);

    KnowledgeArticleResponse get(Long id, Long operatorUserId, boolean canManageAll);

    List<OpsKnowledgeAuditLog> auditLogs(Long id);

    Boolean submitReview(Long id, Long operatorUserId);

    Boolean publish(Long id, Long operatorUserId);

    Boolean archive(Long id, Long operatorUserId);

    Boolean deprecate(Long id, Long operatorUserId);

    Boolean deleteArticle(Long id, Long operatorUserId, boolean canManageAll);
}
