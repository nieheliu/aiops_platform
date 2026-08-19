package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.common.KnowledgeEntrySource;
import com.ops.ai.platform.common.KnowledgeLifecycle;
import com.ops.ai.platform.dto.KnowledgeFacetsResponse;
import com.ops.ai.platform.dto.KnowledgeSearchQuery;
import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;
import com.ops.ai.platform.dto.TicketResolveRequest;
import com.ops.ai.platform.util.KnowledgeComponentUtil;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.es.document.OpsTicketKnowledgeDocument;
import com.ops.ai.platform.mapper.AiDiagnosisMapper;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.service.DashboardCacheService;
import com.ops.ai.platform.service.OllamaEmbeddingService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.OpsTicketLogService;
import com.ops.ai.platform.service.OpsTicketService;
import com.ops.ai.platform.service.SysUserService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.json.JsonData;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketKnowledgeServiceImpl implements TicketKnowledgeService {

    private final OpsTicketService opsTicketService;
    private final SysUserService sysUserService;
    private final OpsTicketLogService opsTicketLogService;
    private final OpsKnowledgeService opsKnowledgeService;
    private final DashboardCacheService dashboardCacheService;
    private final AiDiagnosisMapper aiDiagnosisMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final OllamaEmbeddingService ollamaEmbeddingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean startTicket(Long ticketId, Long operatorUserId) {
        OpsTicket ticket = getTicket(ticketId);
        assertOperatorCanProcess(ticket, operatorUserId, true);
        if (ticket.getStatus() != 0) {
            throw new IllegalStateException("只有待处理工单可以开始处理");
        }
        Long handlerUserId = requireEnabledUserId(operatorUserId, "开始处理");
        ticket.setStatus(1);
        ticket.setHandlerUserId(handlerUserId);
        opsTicketService.updateById(ticket);
        saveLog(ticketId, operatorUserId, "START_PROCESS", "开始处理工单，处理人：" + resolveUsername(handlerUserId));
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignHandler(Long ticketId, Long handlerUserId, Long operatorUserId) {
        requireAdmin(operatorUserId);
        OpsTicket ticket = getTicket(ticketId);
        if (ticket.getStatus() == 3) {
            throw new IllegalStateException("已关闭工单不可变更处理人");
        }
        Long assigneeId = requireEnabledUserId(handlerUserId, "分配处理人");
        ticket.setHandlerUserId(assigneeId);
        opsTicketService.updateById(ticket);
        saveLog(ticketId, operatorUserId, "ASSIGN_HANDLER", "分配处理人：" + resolveUsername(assigneeId));
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean resolveTicket(Long ticketId, TicketResolveRequest request, Long operatorUserId) {
        OpsTicket ticket = getTicket(ticketId);
        assertOperatorCanProcess(ticket, operatorUserId, false);
        if (ticket.getStatus() != 1) {
            throw new IllegalStateException("只有处理中工单可以标记已解决");
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(2);
        ticket.setResolveTime(now);
        opsTicketService.updateById(ticket);

        AiDiagnosis diagnosis = aiDiagnosisMapper.selectOne(new LambdaQueryWrapper<AiDiagnosis>()
                .eq(AiDiagnosis::getTicketId, ticketId)
                .orderByDesc(AiDiagnosis::getCreateTime)
                .last("LIMIT 1"));

        String experienceSummary = request == null ? null : request.getExperienceSummary();
        OpsKnowledge knowledge = upsertKnowledge(ticket, diagnosis, experienceSummary, operatorUserId);
        syncKnowledgeDocument(knowledge);
        saveLog(ticketId, operatorUserId, "RESOLVE", "工单已解决：" + (StringUtils.hasText(experienceSummary) ? experienceSummary : "无经验总结"));
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean closeTicket(Long ticketId, Long operatorUserId) {
        OpsTicket ticket = getTicket(ticketId);
        assertOperatorCanProcess(ticket, operatorUserId, false);
        if (ticket.getStatus() != 2) {
            throw new IllegalStateException("只有已解决工单可以关闭");
        }
        ticket.setStatus(3);
        opsTicketService.updateById(ticket);
        saveLog(ticketId, operatorUserId, "CLOSE", "关闭工单");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteDocument(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("知识文档 ID 不能为空");
        }
        deleteMysqlKnowledge(documentId);
        deleteElasticsearchDocument(documentId);
        dashboardCacheService.evictSummary();
        return true;
    }

    private void deleteMysqlKnowledge(String documentId) {
        if (documentId.startsWith("diagnosis_")) {
            Long diagnosisId = parseLongSuffix(documentId, "diagnosis_");
            if (diagnosisId != null) {
                opsKnowledgeService.remove(new LambdaQueryWrapper<OpsKnowledge>()
                        .eq(OpsKnowledge::getSourceDiagnosisId, diagnosisId));
            }
            return;
        }
        if (documentId.startsWith("ticket_")) {
            Long ticketId = parseLongSuffix(documentId, "ticket_");
            if (ticketId != null) {
                opsKnowledgeService.remove(new LambdaQueryWrapper<OpsKnowledge>()
                        .eq(OpsKnowledge::getSourceTicketId, ticketId)
                        .isNull(OpsKnowledge::getSourceDiagnosisId));
            }
            return;
        }
        if (documentId.startsWith("knowledge_")) {
            Long knowledgeId = parseLongSuffix(documentId, "knowledge_");
            if (knowledgeId != null) {
                opsKnowledgeService.removeById(knowledgeId);
            }
        }
    }

    @Override
    public void removeElasticsearchDocument(String documentId) {
        deleteElasticsearchDocument(documentId);
    }

    @Override
    public String resolveDocumentId(OpsKnowledge knowledge) {
        if (knowledge == null || knowledge.getId() == null) {
            return null;
        }
        if (knowledge.getSourceDiagnosisId() != null) {
            return "diagnosis_" + knowledge.getSourceDiagnosisId();
        }
        if (knowledge.getSourceTicketId() != null && KnowledgeEntrySource.TICKET_RESOLVE.equals(knowledge.getEntrySource())) {
            return "ticket_" + knowledge.getSourceTicketId();
        }
        return "knowledge_" + knowledge.getId();
    }

    @Override
    public void syncKnowledgeDocument(OpsKnowledge knowledge) {
        if (knowledge == null || knowledge.getId() == null) {
            return;
        }
        if (!KnowledgeLifecycle.PUBLISHED.equals(knowledge.getLifecycleStatus())
                && !KnowledgeLifecycle.ARCHIVED.equals(knowledge.getLifecycleStatus())) {
            removeElasticsearchDocument(resolveDocumentId(knowledge));
            return;
        }
        try {
            OpsTicketKnowledgeDocument document = buildDocument(knowledge);
            ensureIndexExists();
            elasticsearchOperations.save(document);
            knowledge.setSyncEsStatus(1);
            opsKnowledgeService.updateById(knowledge);
        } catch (Exception e) {
            log.warn("Sync knowledge document failed, knowledgeId={}", knowledge.getId(), e);
        }
    }

    private void deleteElasticsearchDocument(String documentId) {
        try {
            if (!indexExists()) {
                return;
            }
            elasticsearchOperations.delete(documentId, OpsTicketKnowledgeDocument.class);
        } catch (Exception e) {
            log.warn("Delete ticket knowledge document from Elasticsearch failed, documentId={}", documentId, e);
            throw new IllegalStateException("删除 Elasticsearch 知识文档失败");
        }
    }

    private Long parseLongSuffix(String value, String prefix) {
        try {
            return Long.parseLong(value.substring(prefix.length()));
        } catch (Exception e) {
            log.warn("Invalid knowledge document id: {}", value);
            return null;
        }
    }

    @Override
    public long countDocuments() {
        try {
            if (!indexExists()) {
                return 0L;
            }
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withPageable(PageRequest.of(0, 1))
                    .build();
            return elasticsearchOperations.search(query, OpsTicketKnowledgeDocument.class).getTotalHits();
        } catch (Exception e) {
            log.warn("Count ticket knowledge documents failed", e);
            return 0L;
        }
    }

    @Override
    public TicketKnowledgeSearchResponse search(KnowledgeSearchQuery query) {
        KnowledgeSearchQuery safeQuery = query == null ? new KnowledgeSearchQuery() : query;
        TicketKnowledgeSearchResponse response = emptyResponse(safeQuery.getPage(), safeQuery.getSize());
        try {
            if (!indexExists()) {
                return response;
            }
            int pageIndex = Math.max(safeQuery.getPage(), 1) - 1;
            int pageSize = Math.max(safeQuery.getSize(), 1);
            SearchHits<OpsTicketKnowledgeDocument> hits = searchWithOptionalHighlight(safeQuery, pageIndex, pageSize);
            response.setTotal(hits.getTotalHits());
            response.setRecords(hits.stream().map(this::toRecord).toList());
            return response;
        } catch (Exception e) {
            log.warn("Search ticket knowledge from Elasticsearch failed", e);
            return response;
        }
    }

    @Override
    public KnowledgeFacetsResponse facets(KnowledgeSearchQuery query) {
        KnowledgeFacetsResponse facets = new KnowledgeFacetsResponse();
        KnowledgeSearchQuery safeQuery = query == null ? new KnowledgeSearchQuery() : query;
        try {
            if (!indexExists()) {
                return facets;
            }
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(q -> applySearchBoolQuery(q, safeQuery))
                    .withPageable(PageRequest.of(0, 1000))
                    .build();
            SearchHits<OpsTicketKnowledgeDocument> hits = elasticsearchOperations.search(nativeQuery, OpsTicketKnowledgeDocument.class);
            Map<String, Long> sourceTypes = new LinkedHashMap<>();
            Map<String, Long> components = new LinkedHashMap<>();
            Map<String, Long> aiModels = new LinkedHashMap<>();
            Map<String, Long> lifecycleStatuses = new LinkedHashMap<>();
            hits.forEach(hit -> {
                OpsTicketKnowledgeDocument doc = hit.getContent();
                incrementFacet(sourceTypes, defaultValue(doc.getSourceType(), "unknown"));
                incrementFacet(components, defaultValue(doc.getComponent(), "other"));
                if (StringUtils.hasText(doc.getAiModel())) {
                    incrementFacet(aiModels, doc.getAiModel());
                }
                incrementFacet(lifecycleStatuses, defaultValue(doc.getLifecycleStatus(), KnowledgeLifecycle.PUBLISHED));
            });
            facets.setSourceTypes(sourceTypes);
            facets.setComponents(components);
            facets.setAiModels(aiModels);
            facets.setLifecycleStatuses(lifecycleStatuses);
            return facets;
        } catch (Exception e) {
            log.warn("Build knowledge facets failed", e);
            return facets;
        }
    }

    private SearchHits<OpsTicketKnowledgeDocument> searchWithOptionalHighlight(KnowledgeSearchQuery safeQuery, int pageIndex, int pageSize) {
        try {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(q -> applySearchBoolQuery(q, safeQuery))
                    .withPageable(PageRequest.of(pageIndex, pageSize))
                    .withHighlightQuery(new HighlightQuery(new Highlight(HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build(),
                            List.of(new HighlightField("title"), new HighlightField("description"), new HighlightField("experienceSummary"),
                                    new HighlightField("aiRootCause"), new HighlightField("aiSuggestedFix"))),
                            OpsTicketKnowledgeDocument.class))
                    .build();
            return elasticsearchOperations.search(nativeQuery, OpsTicketKnowledgeDocument.class);
        } catch (Exception highlightError) {
            log.warn("Search with highlight failed, retry without highlight", highlightError);
            NativeQuery fallbackQuery = NativeQuery.builder()
                    .withQuery(q -> applySearchBoolQuery(q, safeQuery))
                    .withPageable(PageRequest.of(pageIndex, pageSize))
                    .build();
            return elasticsearchOperations.search(fallbackQuery, OpsTicketKnowledgeDocument.class);
        }
    }

    private co.elastic.clients.util.ObjectBuilder<co.elastic.clients.elasticsearch._types.query_dsl.Query> applySearchBoolQuery(
            co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder queryBuilder,
            KnowledgeSearchQuery query) {
        return queryBuilder.bool(b -> {
            String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : "";
            if (keyword.isBlank()) {
                b.must(m -> m.matchAll(ma -> ma));
            } else {
                b.must(m -> m.multiMatch(mm -> mm.query(keyword)
                        .fields("title^3", "experienceSummary^2", "contentMd^2", "description", "aiRootCause", "aiSuggestedFix")));
            }
            if (StringUtils.hasText(query.getSourceType())) {
                b.filter(f -> f.term(t -> t.field("sourceType").value(query.getSourceType().trim())));
            }
            if (StringUtils.hasText(query.getComponent())) {
                b.filter(f -> f.term(t -> t.field("component").value(query.getComponent().trim())));
            }
            if (StringUtils.hasText(query.getAiModel())) {
                b.filter(f -> f.term(t -> t.field("aiModel").value(query.getAiModel().trim())));
            }
            if (StringUtils.hasText(query.getLifecycleStatus())) {
                b.filter(f -> f.term(t -> t.field("lifecycleStatus").value(query.getLifecycleStatus().trim())));
            }
            return b;
        });
    }

    private OpsTicket getTicket(Long ticketId) {
        OpsTicket ticket = opsTicketService.getById(ticketId);
        if (ticket == null) throw new IllegalArgumentException("工单不存在");
        return ticket;
    }

    private OpsKnowledge upsertKnowledge(OpsTicket ticket, AiDiagnosis diagnosis, String experienceSummary, Long operatorUserId) {
        OpsKnowledge knowledge = opsKnowledgeService.getOne(new LambdaQueryWrapper<OpsKnowledge>()
                .eq(OpsKnowledge::getSourceTicketId, ticket.getId())
                .isNull(OpsKnowledge::getSourceDiagnosisId)
                .last("LIMIT 1"));
        if (knowledge == null) {
            knowledge = new OpsKnowledge();
            knowledge.setCreatedBy(operatorUserId);
            knowledge.setVersion(1);
        }
        knowledge.setTitle(ticket.getTitle());
        knowledge.setContentMd(buildKnowledgeContent(ticket, diagnosis, experienceSummary));
        knowledge.setTags("[\"工单经验\",\"AI诊断\"]");
        knowledge.setSyncEsStatus(1);
        knowledge.setSourceAlertId(ticket.getAlertId());
        knowledge.setSourceTicketId(ticket.getId());
        knowledge.setEntrySource(KnowledgeEntrySource.TICKET_RESOLVE);
        knowledge.setLifecycleStatus(KnowledgeLifecycle.PUBLISHED);
        knowledge.setComponent(KnowledgeComponentUtil.inferComponent(ticket.getTitle(), ticket.getDescription(), experienceSummary));
        knowledge.setUpdatedBy(operatorUserId);
        if (knowledge.getVersion() == null) {
            knowledge.setVersion(1);
        }
        if (knowledge.getId() == null) {
            opsKnowledgeService.save(knowledge);
        } else {
            opsKnowledgeService.updateById(knowledge);
        }
        return knowledge;
    }

    private String buildKnowledgeContent(OpsTicket ticket, AiDiagnosis diagnosis, String experienceSummary) {
        return "# " + ticket.getTitle() + "\n\n## 工单描述\n\n" + nullToEmpty(ticket.getDescription())
                + "\n\n## AI 根因分析\n\n" + (diagnosis == null ? "暂无" : nullToEmpty(diagnosis.getRootCauseAnalysis()))
                + "\n\n## AI 修复建议\n\n" + (diagnosis == null ? "暂无" : nullToEmpty(diagnosis.getSuggestedFix()))
                + "\n\n## 经验总结\n\n" + nullToEmpty(experienceSummary);
    }

    private OpsTicketKnowledgeDocument buildDocument(OpsKnowledge knowledge) {
        OpsTicket ticket = knowledge.getSourceTicketId() == null ? null : opsTicketService.getById(knowledge.getSourceTicketId());
        AiDiagnosis diagnosis = knowledge.getSourceDiagnosisId() == null ? null
                : aiDiagnosisMapper.selectById(knowledge.getSourceDiagnosisId());
        OpsTicketKnowledgeDocument document = new OpsTicketKnowledgeDocument();
        document.setId(resolveDocumentId(knowledge));
        document.setKnowledgeId(knowledge.getId());
        document.setTicketId(knowledge.getSourceTicketId());
        document.setAlertId(knowledge.getSourceAlertId());
        document.setDiagnosisId(knowledge.getSourceDiagnosisId());
        document.setTitle(knowledge.getTitle());
        document.setContentMd(knowledge.getContentMd());
        document.setDescription(ticket == null ? null : ticket.getDescription());
        document.setExperienceSummary(extractExperienceSummary(knowledge.getContentMd()));
        document.setLifecycleStatus(knowledge.getLifecycleStatus());
        document.setVersion(knowledge.getVersion());
        document.setComponent(knowledge.getComponent());
        document.setCreatedByName(resolveUsername(knowledge.getCreatedBy()));
        document.setUpdatedByName(resolveUsername(knowledge.getUpdatedBy()));
        document.setCreatedAt(knowledge.getCreateTime());
        document.setIndexedAt(LocalDateTime.now());
        document.setResolvedAt(knowledge.getUpdateTime());
        document.setSourceType(StringUtils.hasText(knowledge.getEntrySource()) ? knowledge.getEntrySource() : KnowledgeEntrySource.MANUAL_IMPORT);
        if (ticket != null) {
            document.setStatus(ticket.getStatus());
            document.setHandlerUserId(ticket.getHandlerUserId());
        }
        if (diagnosis != null) {
            document.setAiRootCause(diagnosis.getRootCauseAnalysis());
            document.setAiSuggestedFix(diagnosis.getSuggestedFix());
            document.setAiModel(diagnosis.getAiModel());
        }
        // RAG 向量检索用 embedding：拼接知识全文生成，失败不阻塞写入（返回 null，检索端降级）
        document.setEmbedding(ollamaEmbeddingService.embed(buildEmbeddingText(document)));
        return document;
    }

    private String buildEmbeddingText(OpsTicketKnowledgeDocument document) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(document.getTitle())) sb.append(document.getTitle()).append('\n');
        if (StringUtils.hasText(document.getDescription())) sb.append(document.getDescription()).append('\n');
        if (StringUtils.hasText(document.getExperienceSummary())) sb.append(document.getExperienceSummary()).append('\n');
        if (StringUtils.hasText(document.getAiRootCause())) sb.append(document.getAiRootCause()).append('\n');
        if (StringUtils.hasText(document.getAiSuggestedFix())) sb.append(document.getAiSuggestedFix()).append('\n');
        if (StringUtils.hasText(document.getContentMd())) sb.append(document.getContentMd());
        return sb.toString().trim();
    }

    @Override
    public void assertOperatorCanProcess(Long ticketId, Long operatorUserId, boolean allowClaimIfPending) {
        assertOperatorCanProcess(getTicket(ticketId), operatorUserId, allowClaimIfPending);
    }

    private void assertOperatorCanProcess(OpsTicket ticket, Long operatorUserId, boolean allowClaimIfPending) {
        if (operatorUserId == null) {
            throw new IllegalStateException("未登录用户无法操作工单");
        }
        if (isAdmin(operatorUserId)) {
            return;
        }
        Long handlerId = ticket.getHandlerUserId();
        if (handlerId == null) {
            if (allowClaimIfPending && ticket.getStatus() != null && ticket.getStatus() == 0) {
                return;
            }
            throw new IllegalStateException("该工单尚未分配给您，无法操作");
        }
        if (!handlerId.equals(operatorUserId)) {
            throw new IllegalStateException("仅当前处理人或管理员可操作该工单");
        }
    }

    private boolean isAdmin(Long operatorUserId) {
        return sysUserService.getUserRoles(operatorUserId).stream()
                .map(SysRole::getRoleCode)
                .anyMatch("ADMIN"::equals);
    }

    private void requireAdmin(Long operatorUserId) {
        if (operatorUserId == null) {
            throw new IllegalStateException("仅管理员可分配处理人");
        }
        if (!isAdmin(operatorUserId)) {
            throw new IllegalStateException("仅管理员可分配处理人，运维人员请通过「开始处理」认领工单");
        }
    }

    private Long requireEnabledUserId(Long userId, String action) {
        if (userId == null) {
            throw new IllegalArgumentException(action + "需要有效的当前用户");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("处理人用户不存在或已禁用");
        }
        return userId;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        SysUser user = sysUserService.getById(userId);
        return user == null ? ("用户 " + userId) : user.getUsername();
    }

    private void saveLog(Long ticketId, Long operatorUserId, String action, String remark) {
        try {
            Long operatorId = operatorUserId == null ? 1L : operatorUserId;
            OpsTicketLog log = new OpsTicketLog();
            log.setTicketId(ticketId);
            log.setOperatorId(operatorId);
            log.setAction(action);
            log.setRemark(remark);
            log.setOperateTime(LocalDateTime.now());
            opsTicketLogService.save(log);
        } catch (Exception e) {
            log.warn("Save ticket log failed, ticketId={}, action={}", ticketId, action, e);
        }
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private boolean indexExists() { try { return elasticsearchOperations.indexOps(OpsTicketKnowledgeDocument.class).exists(); } catch (Exception e) { log.warn("Check ticket knowledge index failed", e); return false; } }
    private void ensureIndexExists() { IndexOperations indexOps = elasticsearchOperations.indexOps(OpsTicketKnowledgeDocument.class); if (!indexOps.exists()) indexOps.createWithMapping(); }
    private TicketKnowledgeSearchResponse emptyResponse(int page, int size) { TicketKnowledgeSearchResponse response = new TicketKnowledgeSearchResponse(); response.setPage(page); response.setSize(size); response.setTotal(0L); response.setRecords(new ArrayList<>()); return response; }

    private TicketKnowledgeSearchResponse.Record toRecord(SearchHit<OpsTicketKnowledgeDocument> hit) {
        OpsTicketKnowledgeDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();
        TicketKnowledgeSearchResponse.Record record = new TicketKnowledgeSearchResponse.Record();
        Long ticketId = doc.getTicketId();
        if (ticketId == null && doc.getAlertId() != null) {
            OpsTicket ticket = opsTicketService.getOne(new LambdaQueryWrapper<OpsTicket>()
                    .eq(OpsTicket::getAlertId, doc.getAlertId())
                    .orderByDesc(OpsTicket::getCreateTime)
                    .last("LIMIT 1"));
            if (ticket != null) {
                ticketId = ticket.getId();
            }
        }
        record.setTicketId(ticketId);
        record.setAlertId(doc.getAlertId());
        record.setDiagnosisId(doc.getDiagnosisId());
        record.setTitle(doc.getTitle());
        record.setDescription(doc.getDescription());
        record.setAiRootCause(doc.getAiRootCause());
        record.setAiSuggestedFix(doc.getAiSuggestedFix());
        record.setExperienceSummary(doc.getExperienceSummary());
        record.setResolvedAt(doc.getResolvedAt());
        record.setIndexedAt(doc.getIndexedAt());
        record.setSourceType(doc.getSourceType());
        record.setAiModel(doc.getAiModel());
        record.setDocumentId(hit.getId());
        record.setKnowledgeId(doc.getKnowledgeId());
        record.setLifecycleStatus(doc.getLifecycleStatus());
        record.setVersion(doc.getVersion());
        record.setComponent(doc.getComponent());
        record.setCreatedByName(doc.getCreatedByName());
        record.setUpdatedByName(doc.getUpdatedByName());
        record.setContentMd(doc.getContentMd());
        record.setTitleHighlight(firstHighlight(highlights, "title", doc.getTitle()));
        record.setDescriptionHighlight(firstHighlight(highlights, "description", doc.getDescription()));
        record.setAiRootCauseHighlight(firstHighlight(highlights, "aiRootCause", doc.getAiRootCause()));
        record.setAiSuggestedFixHighlight(firstHighlight(highlights, "aiSuggestedFix", doc.getAiSuggestedFix()));
        record.setExperienceSummaryHighlight(firstHighlight(highlights, "experienceSummary", doc.getExperienceSummary()));
        return record;
    }

    private String firstHighlight(Map<String, List<String>> highlights, String field, String fallback) { List<String> values = highlights.get(field); return values == null || values.isEmpty() ? fallback : values.get(0); }

    private void incrementFacet(Map<String, Long> bucket, String key) {
        bucket.put(key, bucket.getOrDefault(key, 0L) + 1);
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private LocalDateTime parseDateStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String extractExperienceSummary(String contentMd) {
        if (!StringUtils.hasText(contentMd)) {
            return null;
        }
        String marker = "## 经验总结";
        int index = contentMd.indexOf(marker);
        if (index < 0) {
            return contentMd.length() > 300 ? contentMd.substring(0, 300) : contentMd;
        }
        return contentMd.substring(index + marker.length()).trim();
    }
}
