package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ops.ai.platform.config.AiOpsModelProperties;
import com.ops.ai.platform.config.RagProperties;
import com.ops.ai.platform.dto.RagContextItem;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.common.KnowledgeEntrySource;
import com.ops.ai.platform.common.KnowledgeLifecycle;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.util.KnowledgeComponentUtil;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.es.document.OpsTicketKnowledgeDocument;
import com.ops.ai.platform.service.AiChatClientFactory;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import com.ops.ai.platform.service.AiModelService;
import com.ops.ai.platform.service.DashboardCacheService;
import com.ops.ai.platform.service.OpsAlertService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.OpsTicketService;
import com.ops.ai.platform.service.RagRetrievalService;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisWorkflowServiceImpl implements AiDiagnosisWorkflowService {

    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("(?i)confidence\\s*[:：]\\s*(\\d{1,3}(?:\\.\\d+)?)");

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private static final ConcurrentHashMap<String, Object> DIAGNOSE_LOCKS = new ConcurrentHashMap<>();

    /**
     * 用于解析嵌套 JSON 的宽容解析器：允许字符串内存在未转义控制字符。
     * 模型偶尔会把整个诊断对象作为字符串塞进 rootCauseAnalysis 字段，此时内层 JSON
     * 的 \n 已是真实换行符，标准解析器会拒绝，需要该配置。
     */
    private static final ObjectMapper NESTED_JSON_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private final OpsAlertService opsAlertService;

    private final OpsTicketService opsTicketService;

    private final AiDiagnosisService aiDiagnosisService;

    private final OpsKnowledgeService opsKnowledgeService;

    private final AiModelService aiModelService;

    private final AiChatClientFactory aiChatClientFactory;

    private final DashboardCacheService dashboardCacheService;

    private final TicketKnowledgeService ticketKnowledgeService;

    private final RagRetrievalService ragRetrievalService;

    private final RagProperties ragProperties;

    private final ObjectMapper objectMapper;

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public AiDiagnosis diagnoseAlert(Long alertId, String modelId) {
        return diagnoseAlertTicket(alertId, null, modelId);
    }

    @Override
    public AiDiagnosis diagnoseTicket(Long ticketId, String modelId, Long operatorUserId) {
        OpsTicket ticket = opsTicketService.getById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found, ticketId=" + ticketId);
        }
        boolean allowClaim = ticket.getStatus() != null && ticket.getStatus() == 0;
        ticketKnowledgeService.assertOperatorCanProcess(ticketId, operatorUserId, allowClaim);
        return diagnoseAlertTicket(ticket.getAlertId(), ticketId, modelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDiagnosis diagnoseAlertTicket(Long alertId, Long ticketId, String modelId) {
        String resolvedModelId = StringUtils.hasText(modelId) ? modelId : aiModelService.getDefaultModelId();
        AiOpsModelProperties.ModelDefinition modelDefinition = aiModelService.requireModel(resolvedModelId);

        OpsAlert alert = opsAlertService.getById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found, alertId=" + alertId);
        }

        OpsTicket ticket = ticketId == null ? null : opsTicketService.getById(ticketId);
        if (ticketId != null && ticket == null) {
            throw new IllegalArgumentException("Ticket not found, ticketId=" + ticketId);
        }
        if (ticket == null) {
            ticket = findTicketByAlertId(alertId);
            if (ticket != null) {
                ticketId = ticket.getId();
            }
        }

        Long scopedAlertId = alertId;
        Long scopedTicketId = ticketId;
        Object lock = diagnoseLock(scopedAlertId, scopedTicketId, resolvedModelId);
        synchronized (lock) {
            AiDiagnosis existing = aiModelService.findExistingDiagnosis(scopedAlertId, scopedTicketId, resolvedModelId);
            if (existing != null) {
                log.info("Diagnosis already exists, return cached result. alertId={}, ticketId={}, modelId={}, diagnosisId={}",
                        scopedAlertId, scopedTicketId, resolvedModelId, existing.getId());
                return existing;
            }

            List<RagContextItem> ragContext = retrieveRagContext(alert, ticket);
            String prompt = buildPrompt(alert, ticket, ragContext);
            ChatClient chatClient = aiChatClientFactory.createClient(modelDefinition);
            String response = invokeChat(chatClient, prompt, modelDefinition.isStreamOnly());

            existing = aiModelService.findExistingDiagnosis(scopedAlertId, scopedTicketId, resolvedModelId);
            if (existing != null) {
                log.info("Diagnosis created concurrently, return existing result. alertId={}, ticketId={}, modelId={}, diagnosisId={}",
                        scopedAlertId, scopedTicketId, resolvedModelId, existing.getId());
                return existing;
            }

            AiDiagnosis diagnosis = new AiDiagnosis();
            diagnosis.setAlertId(scopedAlertId);
            diagnosis.setTicketId(scopedTicketId);
            diagnosis.setAiModel(modelDefinition.getId());
            fillDiagnosisFromResponse(diagnosis, response);
            aiDiagnosisService.save(diagnosis);
            return diagnosis;
        }
    }

    private Object diagnoseLock(Long alertId, Long ticketId, String modelId) {
        String key = alertId + ":" + (ticketId != null ? ticketId : "none") + ":" + modelId;
        return DIAGNOSE_LOCKS.computeIfAbsent(key, ignored -> new Object());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpsKnowledge convertDiagnosisToKnowledge(Long diagnosisId) {
        AiDiagnosis diagnosis = aiDiagnosisService.getById(diagnosisId);
        if (diagnosis == null) {
            throw new IllegalArgumentException("Diagnosis not found, diagnosisId=" + diagnosisId);
        }

        OpsTicket ticket = diagnosis.getTicketId() == null ? null : opsTicketService.getById(diagnosis.getTicketId());
        if (ticket == null) {
            ticket = findTicketByAlertId(diagnosis.getAlertId());
        }
        OpsAlert alert = diagnosis.getAlertId() == null ? null : opsAlertService.getById(diagnosis.getAlertId());

        OpsKnowledge knowledge = findExistingKnowledge(diagnosis.getId());
        if (knowledge == null) {
            knowledge = new OpsKnowledge();
        }
        knowledge.setTitle(buildKnowledgeTitle(ticket, alert, diagnosis));
        knowledge.setContentMd(buildKnowledgeContent(ticket, alert, diagnosis));
        knowledge.setTags("[\"大模型诊断\",\"AI诊断\"]");
        knowledge.setSyncEsStatus(1);
        knowledge.setSourceAlertId(diagnosis.getAlertId());
        knowledge.setSourceTicketId(ticket == null ? diagnosis.getTicketId() : ticket.getId());
        knowledge.setSourceDiagnosisId(diagnosis.getId());
        knowledge.setEntrySource(KnowledgeEntrySource.DIAGNOSIS_IMPORT);
        knowledge.setLifecycleStatus(KnowledgeLifecycle.PUBLISHED);
        knowledge.setVersion(knowledge.getVersion() == null ? 1 : knowledge.getVersion() + 1);
        knowledge.setComponent(KnowledgeComponentUtil.inferComponent(
                knowledge.getTitle(), diagnosis.getRootCauseAnalysis(), diagnosis.getSuggestedFix()));
        if (knowledge.getId() == null) {
            opsKnowledgeService.save(knowledge);
        } else {
            opsKnowledgeService.updateById(knowledge);
        }

        ticketKnowledgeService.syncKnowledgeDocument(knowledge);
        dashboardCacheService.evictSummary();
        return knowledge;
    }

    private OpsKnowledge findExistingKnowledge(Long diagnosisId) {
        if (diagnosisId == null) {
            return null;
        }
        return opsKnowledgeService.getOne(new LambdaQueryWrapper<OpsKnowledge>()
                .eq(OpsKnowledge::getSourceDiagnosisId, diagnosisId)
                .last("LIMIT 1"));
    }

    private List<RagContextItem> retrieveRagContext(OpsAlert alert, OpsTicket ticket) {
        if (!ragProperties.isEnabled()) {
            return List.of();
        }
        String keyword = buildRagKeyword(alert, ticket);
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return ragRetrievalService.retrieveRelated(keyword, ragProperties.getTopK());
    }

    private String buildRagKeyword(OpsAlert alert, OpsTicket ticket) {
        StringBuilder sb = new StringBuilder();
        if (alert != null && StringUtils.hasText(alert.getAlertName())) {
            sb.append(alert.getAlertName()).append(' ');
        }
        if (ticket != null && StringUtils.hasText(ticket.getTitle())) {
            // 工单标题含 "[P4] 告警名 - IP"，去掉结构化前缀与 IP，保留核心告警名
            String title = ticket.getTitle().replaceAll("^\\[P\\d+\\]\\s*", "");
            sb.append(title.replaceAll("-\\s*\\d+(\\.\\d+){3}$", ""));
        }
        return sb.toString().trim();
    }

    private String buildPrompt(OpsAlert alert, OpsTicket ticket, List<RagContextItem> ragContext) {
        return "你是资深 SRE 运维专家，请根据以下告警和工单信息生成故障诊断报告。\n"
                + "请优先返回严格 JSON，不要使用 Markdown 代码块，格式如下：\n"
                + "{\"rootCauseAnalysis\":\"## 根因分析\\n...\",\"suggestedFix\":\"## 影响范围\\n...\\n## 排查步骤\\n...\\n## 修复建议\\n...\\n## 风险提示\\n...\\n## 后续预防措施\\n...\",\"confidenceScore\":86}\n"
                + "如果无法返回 JSON，则使用 ROOT_CAUSE、SUGGESTED_FIX、CONFIDENCE 三个小标题。\n\n"
                + (ragContext == null || ragContext.isEmpty() ? "" : buildRagContextSection(ragContext))
                + "告警信息：\n"
                + "- 告警ID: " + alert.getId() + "\n"
                + "- 告警名称: " + alert.getAlertName() + "\n"
                + "- 严重等级: " + alert.getSeverity() + "\n"
                + "- 实例IP: " + alert.getInstanceIp() + "\n"
                + "- 触发时间: " + alert.getTriggerTime() + "\n"
                + "- 原始载荷: " + safeText(alert.getRawPayload()) + "\n\n"
                + (ticket == null ? "" :
                "工单信息：\n"
                        + "- 工单ID: " + ticket.getId() + "\n"
                        + "- 工单标题: " + ticket.getTitle() + "\n"
                        + "- 工单描述: " + safeText(ticket.getDescription()) + "\n"
                        + "- 工单状态: " + ticket.getStatus() + "\n\n")
                + "请直接输出结果，不要附加多余解释。";
    }

    private String buildRagContextSection(List<RagContextItem> ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 历史相似案例参考（RAG）\n");
        sb.append("以下为知识库中与本次告警相似的历史案例，可参考其根因与处理经验，但需结合本次告警实际情况判断：\n");
        int index = 1;
        for (RagContextItem item : ragContext) {
            sb.append("--- 案例 ").append(index++).append(" ---\n");
            if (StringUtils.hasText(item.getTitle())) {
                sb.append("标题：").append(item.getTitle()).append('\n');
            }
            if (StringUtils.hasText(item.getRootCause())) {
                sb.append("历史根因：").append(item.getRootCause()).append('\n');
            }
            if (StringUtils.hasText(item.getSuggestedFix())) {
                sb.append("历史修复建议：").append(item.getSuggestedFix()).append('\n');
            }
            if (StringUtils.hasText(item.getExperienceSummary())) {
                sb.append("经验总结：").append(item.getExperienceSummary()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void fillDiagnosisFromResponse(AiDiagnosis diagnosis, String response) {
        if (tryFillDiagnosisFromJson(diagnosis, response)) {
            return;
        }
        diagnosis.setRootCauseAnalysis(extractSection(response, "ROOT_CAUSE", response));
        diagnosis.setSuggestedFix(extractSection(response, "SUGGESTED_FIX", null));
        diagnosis.setConfidenceScore(extractConfidence(response));
    }

    private boolean tryFillDiagnosisFromJson(AiDiagnosis diagnosis, String response) {
        if (!StringUtils.hasText(response)) {
            return false;
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(response);
        if (!matcher.find()) {
            return false;
        }
        String jsonText = matcher.group();
        JsonNode root = tryParseJson(jsonText);
        if (root == null) {
            // 模型常把建议命令里的引号错误双转义为 \\"，合法 JSON 应为 \"。纠正后再解析。
            String normalized = jsonText.replace("\\\\\"", "\\\"");
            root = tryParseJson(normalized);
            if (root != null) {
                log.info("Diagnosis JSON parsed after fixing double-escaped quotes");
            }
        }
        if (root == null) {
            log.warn("Failed to parse diagnosis JSON response, fallback to section extraction");
            return false;
        }
        root = unwrapNestedDiagnosis(root);
        String rootCauseAnalysis = textValue(root, "rootCauseAnalysis");
        String suggestedFix = textValue(root, "suggestedFix");
        if (!StringUtils.hasText(rootCauseAnalysis) && !StringUtils.hasText(suggestedFix)) {
            return false;
        }
        diagnosis.setRootCauseAnalysis(rootCauseAnalysis);
        diagnosis.setSuggestedFix(suggestedFix);
        JsonNode confidenceNode = root.get("confidenceScore");
        if (confidenceNode != null && confidenceNode.isNumber()) {
            diagnosis.setConfidenceScore(confidenceNode.decimalValue());
        }
        return true;
    }

    /** 分层尝试解析：标准 → 宽容未转义控制字符（嵌套 JSON 内含真实换行） */
    private JsonNode tryParseJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            try {
                return NESTED_JSON_MAPPER.readTree(text);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * 兼容嵌套 JSON：模型偶尔把整个诊断对象作为字符串塞进 rootCauseAnalysis 字段，
     * 此时以内部对象为真正的解析根，保证 confidenceScore 等字段能正确提取。
     */
    private JsonNode unwrapNestedDiagnosis(JsonNode root) {
        JsonNode rootCauseNode = root.get("rootCauseAnalysis");
        if (rootCauseNode == null || !rootCauseNode.isTextual()) {
            return root;
        }
        String inner = rootCauseNode.asText().trim();
        if (!inner.startsWith("{")) {
            return root;
        }
        try {
            JsonNode innerRoot = NESTED_JSON_MAPPER.readTree(inner);
            if (innerRoot.isObject() && (innerRoot.has("rootCauseAnalysis") || innerRoot.has("suggestedFix"))) {
                return innerRoot;
            }
        } catch (Exception e) {
            log.warn("Nested diagnosis JSON parse failed, fallback to outer root", e);
        }
        return root;
    }

    private String textValue(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

    private String extractSection(String response, String sectionName, String defaultValue) {
        if (!StringUtils.hasText(response)) {
            return defaultValue;
        }
        String normalized = response.replace("\r", "");
        String marker = sectionName + ":";
        int start = normalized.indexOf(marker);
        if (start < 0) {
            return defaultValue;
        }
        start += marker.length();
        int end = normalized.length();
        for (String other : new String[]{"ROOT_CAUSE:", "SUGGESTED_FIX:", "CONFIDENCE:"}) {
            if (other.equals(marker)) {
                continue;
            }
            int idx = normalized.indexOf(other, start);
            if (idx >= 0 && idx < end) {
                end = idx;
            }
        }
        return normalized.substring(start, end).trim();
    }

    private BigDecimal extractConfidence(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        Matcher matcher = CONFIDENCE_PATTERN.matcher(response);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (Exception e) {
            log.warn("Failed to parse confidence from response", e);
            return null;
        }
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String invokeChat(ChatClient chatClient, String prompt, boolean streamOnly) {
        if (streamOnly) {
            return chatClient.prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .blockOptional()
                    .orElse("");
        }
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String buildKnowledgeTitle(OpsTicket ticket, OpsAlert alert, AiDiagnosis diagnosis) {
        String baseTitle;
        if (ticket != null && StringUtils.hasText(ticket.getTitle())) {
            baseTitle = ticket.getTitle();
        } else if (alert != null && StringUtils.hasText(alert.getAlertName())) {
            baseTitle = alert.getAlertName();
        } else {
            baseTitle = "大模型诊断报告 #" + diagnosis.getId();
        }
        if (StringUtils.hasText(diagnosis.getAiModel())) {
            return baseTitle + " [" + diagnosis.getAiModel() + "]";
        }
        return baseTitle;
    }

    private String buildKnowledgeContent(OpsTicket ticket, OpsAlert alert, AiDiagnosis diagnosis) {
        return "# " + buildKnowledgeTitle(ticket, alert, diagnosis)
                + "\n\n## 根因分析\n\n" + safeText(diagnosis.getRootCauseAnalysis())
                + "\n\n## 修复建议\n\n" + safeText(diagnosis.getSuggestedFix())
                + "\n\n## 关联信息\n\n"
                + "- 告警ID：" + nullToDash(diagnosis.getAlertId()) + "\n"
                + "- 工单ID：" + nullToDash(ticket == null ? diagnosis.getTicketId() : ticket.getId()) + "\n"
                + "- 模型：" + safeText(diagnosis.getAiModel()) + "\n"
                + "- 置信度：" + nullToDash(diagnosis.getConfidenceScore()) + "\n";
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private OpsTicket findTicketByAlertId(Long alertId) {
        if (alertId == null) {
            return null;
        }
        return opsTicketService.getOne(new LambdaQueryWrapper<OpsTicket>()
                .eq(OpsTicket::getAlertId, alertId)
                .orderByDesc(OpsTicket::getCreateTime)
                .last("LIMIT 1"));
    }

    private void syncKnowledgeToElasticsearch(OpsKnowledge knowledge, OpsTicket ticket, AiDiagnosis diagnosis) {
        try {
            if (ticket == null) {
                ticket = findTicketByAlertId(diagnosis.getAlertId());
            }
            Long ticketId = ticket == null ? diagnosis.getTicketId() : ticket.getId();
            OpsTicketKnowledgeDocument document = new OpsTicketKnowledgeDocument();
            document.setId("diagnosis_" + diagnosis.getId());
            document.setTicketId(ticketId);
            document.setAlertId(diagnosis.getAlertId());
            document.setDiagnosisId(diagnosis.getId());
            document.setTitle(knowledge.getTitle());
            document.setDescription(ticket == null ? null : ticket.getDescription());
            document.setAiRootCause(diagnosis.getRootCauseAnalysis());
            document.setAiSuggestedFix(diagnosis.getSuggestedFix());
            document.setExperienceSummary(knowledge.getContentMd());
            document.setStatus(ticket == null ? null : ticket.getStatus());
            document.setHandlerUserId(ticket == null ? null : ticket.getHandlerUserId());
            document.setSourceType("diagnosis_import");
            document.setAiModel(diagnosis.getAiModel());
            document.setCreatedAt(knowledge.getCreateTime());
            document.setIndexedAt(LocalDateTime.now());
            IndexOperations indexOps = elasticsearchOperations.indexOps(OpsTicketKnowledgeDocument.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
            elasticsearchOperations.save(document);
        } catch (Exception e) {
            log.warn("Sync diagnosis knowledge to Elasticsearch failed, diagnosisId={}", diagnosis.getId(), e);
        }
    }
}
