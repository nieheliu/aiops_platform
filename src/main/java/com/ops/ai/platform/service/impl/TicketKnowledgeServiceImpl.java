package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;
import com.ops.ai.platform.dto.TicketResolveRequest;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.es.document.OpsTicketKnowledgeDocument;
import com.ops.ai.platform.mapper.AiDiagnosisMapper;
import com.ops.ai.platform.service.DashboardCacheService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.OpsTicketLogService;
import com.ops.ai.platform.service.OpsTicketService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketKnowledgeServiceImpl implements TicketKnowledgeService {

    private final OpsTicketService opsTicketService;
    private final OpsTicketLogService opsTicketLogService;
    private final OpsKnowledgeService opsKnowledgeService;
    private final DashboardCacheService dashboardCacheService;
    private final AiDiagnosisMapper aiDiagnosisMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean startTicket(Long ticketId) {
        OpsTicket ticket = getTicket(ticketId);
        if (ticket.getStatus() != 0) {
            throw new IllegalStateException("只有待处理工单可以开始处理");
        }
        ticket.setStatus(1);
        opsTicketService.updateById(ticket);
        saveLog(ticketId, "START_PROCESS", "开始处理工单");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean resolveTicket(Long ticketId, TicketResolveRequest request) {
        OpsTicket ticket = getTicket(ticketId);
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
        upsertKnowledge(ticket, diagnosis, experienceSummary);
        trySaveToElasticsearch(ticket, diagnosis, experienceSummary, now);
        saveLog(ticketId, "RESOLVE", "工单已解决：" + (StringUtils.hasText(experienceSummary) ? experienceSummary : "无经验总结"));
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean closeTicket(Long ticketId) {
        OpsTicket ticket = getTicket(ticketId);
        if (ticket.getStatus() != 2) {
            throw new IllegalStateException("只有已解决工单可以关闭");
        }
        ticket.setStatus(3);
        opsTicketService.updateById(ticket);
        saveLog(ticketId, "CLOSE", "关闭工单");
        dashboardCacheService.evictSummary();
        return true;
    }

    @Override
    public TicketKnowledgeSearchResponse search(String keyword, int page, int size) {
        TicketKnowledgeSearchResponse response = emptyResponse(page, size);
        try {
            if (!indexExists()) return response;
            String searchKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
            int pageIndex = Math.max(page, 1) - 1;
            int pageSize = Math.max(size, 1);
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> searchKeyword.isBlank() ? q.matchAll(m -> m) : q.multiMatch(m -> m.query(searchKeyword).fields("title^3", "experienceSummary^2", "description", "aiRootCause", "aiSuggestedFix")))
                    .withPageable(PageRequest.of(pageIndex, pageSize))
                    .withHighlightQuery(new HighlightQuery(new Highlight(HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build(), List.of(new HighlightField("title"), new HighlightField("description"), new HighlightField("experienceSummary"), new HighlightField("aiRootCause"), new HighlightField("aiSuggestedFix"))), OpsTicketKnowledgeDocument.class))
                    .build();
            SearchHits<OpsTicketKnowledgeDocument> hits = elasticsearchOperations.search(query, OpsTicketKnowledgeDocument.class);
            response.setTotal(hits.getTotalHits());
            response.setRecords(hits.stream().map(this::toRecord).toList());
            return response;
        } catch (Exception e) {
            log.warn("Search ticket knowledge from Elasticsearch failed", e);
            return response;
        }
    }

    private OpsTicket getTicket(Long ticketId) {
        OpsTicket ticket = opsTicketService.getById(ticketId);
        if (ticket == null) throw new IllegalArgumentException("工单不存在");
        return ticket;
    }

    private void upsertKnowledge(OpsTicket ticket, AiDiagnosis diagnosis, String experienceSummary) {
        OpsKnowledge knowledge = opsKnowledgeService.getOne(new LambdaQueryWrapper<OpsKnowledge>().eq(OpsKnowledge::getSourceTicketId, ticket.getId()).last("LIMIT 1"));
        if (knowledge == null) knowledge = new OpsKnowledge();
        knowledge.setTitle(ticket.getTitle());
        knowledge.setContentMd(buildKnowledgeContent(ticket, diagnosis, experienceSummary));
        knowledge.setTags("[\"工单经验\",\"AI诊断\"]");
        knowledge.setSyncEsStatus(1);
        knowledge.setSourceAlertId(ticket.getAlertId());
        knowledge.setSourceTicketId(ticket.getId());
        if (knowledge.getId() == null) opsKnowledgeService.save(knowledge); else opsKnowledgeService.updateById(knowledge);
    }

    private String buildKnowledgeContent(OpsTicket ticket, AiDiagnosis diagnosis, String experienceSummary) {
        return "# " + ticket.getTitle() + "\n\n## 工单描述\n\n" + nullToEmpty(ticket.getDescription())
                + "\n\n## AI 根因分析\n\n" + (diagnosis == null ? "暂无" : nullToEmpty(diagnosis.getRootCauseAnalysis()))
                + "\n\n## AI 修复建议\n\n" + (diagnosis == null ? "暂无" : nullToEmpty(diagnosis.getSuggestedFix()))
                + "\n\n## 经验总结\n\n" + nullToEmpty(experienceSummary);
    }

    private void trySaveToElasticsearch(OpsTicket ticket, AiDiagnosis diagnosis, String experienceSummary, LocalDateTime now) {
        try {
            OpsTicketKnowledgeDocument document = new OpsTicketKnowledgeDocument();
            document.setId("ticket_" + ticket.getId());
            document.setTicketId(ticket.getId());
            document.setAlertId(ticket.getAlertId());
            document.setTitle(ticket.getTitle());
            document.setDescription(ticket.getDescription());
            document.setStatus(ticket.getStatus());
            document.setHandlerUserId(ticket.getHandlerUserId());
            document.setExperienceSummary(experienceSummary);
            document.setResolvedAt(now);
            document.setCreatedAt(ticket.getCreateTime());
            document.setIndexedAt(now);
            if (diagnosis != null) {
                document.setAiRootCause(diagnosis.getRootCauseAnalysis());
                document.setAiSuggestedFix(diagnosis.getSuggestedFix());
            }
            ensureIndexExists();
            elasticsearchOperations.save(document);
        } catch (Exception e) {
            log.warn("Sync ticket knowledge to Elasticsearch failed, ticketId={}", ticket.getId(), e);
        }
    }

    private void saveLog(Long ticketId, String action, String remark) {
        try {
            OpsTicketLog log = new OpsTicketLog();
            log.setTicketId(ticketId);
            log.setOperatorId(1L);
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
        record.setTicketId(doc.getTicketId()); record.setAlertId(doc.getAlertId()); record.setTitle(doc.getTitle()); record.setDescription(doc.getDescription()); record.setAiRootCause(doc.getAiRootCause()); record.setAiSuggestedFix(doc.getAiSuggestedFix()); record.setExperienceSummary(doc.getExperienceSummary()); record.setResolvedAt(doc.getResolvedAt());
        record.setTitleHighlight(firstHighlight(highlights, "title", doc.getTitle())); record.setDescriptionHighlight(firstHighlight(highlights, "description", doc.getDescription())); record.setAiRootCauseHighlight(firstHighlight(highlights, "aiRootCause", doc.getAiRootCause())); record.setAiSuggestedFixHighlight(firstHighlight(highlights, "aiSuggestedFix", doc.getAiSuggestedFix())); record.setExperienceSummaryHighlight(firstHighlight(highlights, "experienceSummary", doc.getExperienceSummary()));
        return record;
    }

    private String firstHighlight(Map<String, List<String>> highlights, String field, String fallback) { List<String> values = highlights.get(field); return values == null || values.isEmpty() ? fallback : values.get(0); }
}
