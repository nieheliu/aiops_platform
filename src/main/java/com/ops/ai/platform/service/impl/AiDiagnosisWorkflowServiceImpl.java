package com.ops.ai.platform.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.es.document.OpsTicketKnowledgeDocument;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import com.ops.ai.platform.service.OpsAlertService;
import com.ops.ai.platform.service.OpsKnowledgeService;
import com.ops.ai.platform.service.OpsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisWorkflowServiceImpl implements AiDiagnosisWorkflowService {

    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("(?i)confidence\\s*[:：]\\s*(\\d{1,3}(?:\\.\\d+)?)");

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final OpsAlertService opsAlertService;

    private final OpsTicketService opsTicketService;

    private final AiDiagnosisService aiDiagnosisService;

    private final OpsKnowledgeService opsKnowledgeService;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    @Override
    public AiDiagnosis diagnoseAlert(Long alertId) {
        return diagnoseAlertTicket(alertId, null);
    }

    @Override
    public AiDiagnosis diagnoseTicket(Long ticketId) {
        OpsTicket ticket = opsTicketService.getById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found, ticketId=" + ticketId);
        }
        return diagnoseAlertTicket(ticket.getAlertId(), ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDiagnosis diagnoseAlertTicket(Long alertId, Long ticketId) {
        OpsAlert alert = opsAlertService.getById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found, alertId=" + alertId);
        }

        OpsTicket ticket = ticketId == null ? null : opsTicketService.getById(ticketId);
        if (ticketId != null && ticket == null) {
            throw new IllegalArgumentException("Ticket not found, ticketId=" + ticketId);
        }

        String prompt = buildPrompt(alert, ticket);
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        AiDiagnosis diagnosis = new AiDiagnosis();
        diagnosis.setAlertId(alertId);
        diagnosis.setTicketId(ticketId);
        diagnosis.setAiModel(modelName);
        fillDiagnosisFromResponse(diagnosis, response);
        aiDiagnosisService.save(diagnosis);
        return diagnosis;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpsKnowledge convertDiagnosisToKnowledge(Long diagnosisId) {
        AiDiagnosis diagnosis = aiDiagnosisService.getById(diagnosisId);
        if (diagnosis == null) {
            throw new IllegalArgumentException("Diagnosis not found, diagnosisId=" + diagnosisId);
        }

        OpsTicket ticket = diagnosis.getTicketId() == null ? null : opsTicketService.getById(diagnosis.getTicketId());
        OpsAlert alert = diagnosis.getAlertId() == null ? null : opsAlertService.getById(diagnosis.getAlertId());

        OpsKnowledge knowledge = new OpsKnowledge();
        knowledge.setTitle(buildKnowledgeTitle(ticket, alert, diagnosis));
        knowledge.setContentMd(buildKnowledgeContent(ticket, alert, diagnosis));
        knowledge.setTags("[\"大模型诊断\",\"AI诊断\"]");
        knowledge.setSyncEsStatus(1);
        knowledge.setSourceAlertId(diagnosis.getAlertId());
        knowledge.setSourceTicketId(diagnosis.getTicketId());
        opsKnowledgeService.save(knowledge);

        syncKnowledgeToElasticsearch(knowledge, ticket, diagnosis);
        return knowledge;
    }

    private String buildPrompt(OpsAlert alert, OpsTicket ticket) {
        return "你是资深 SRE 运维专家，请根据以下告警和工单信息生成故障诊断报告。\n"
                + "请优先返回严格 JSON，不要使用 Markdown 代码块，格式如下：\n"
                + "{\"rootCauseAnalysis\":\"## 根因分析\\n...\",\"suggestedFix\":\"## 影响范围\\n...\\n## 排查步骤\\n...\\n## 修复建议\\n...\\n## 风险提示\\n...\\n## 后续预防措施\\n...\",\"confidenceScore\":86}\n"
                + "如果无法返回 JSON，则使用 ROOT_CAUSE、SUGGESTED_FIX、CONFIDENCE 三个小标题。\n\n"
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
        try {
            JsonNode root = objectMapper.readTree(matcher.group());
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
        } catch (Exception e) {
            log.warn("Failed to parse diagnosis JSON response", e);
            return false;
        }
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

    private String buildKnowledgeTitle(OpsTicket ticket, OpsAlert alert, AiDiagnosis diagnosis) {
        if (ticket != null && StringUtils.hasText(ticket.getTitle())) {
            return ticket.getTitle();
        }
        if (alert != null && StringUtils.hasText(alert.getAlertName())) {
            return alert.getAlertName();
        }
        return "大模型诊断报告 #" + diagnosis.getId();
    }

    private String buildKnowledgeContent(OpsTicket ticket, OpsAlert alert, AiDiagnosis diagnosis) {
        return "# " + buildKnowledgeTitle(ticket, alert, diagnosis)
                + "\n\n## 根因分析\n\n" + safeText(diagnosis.getRootCauseAnalysis())
                + "\n\n## 修复建议\n\n" + safeText(diagnosis.getSuggestedFix())
                + "\n\n## 关联信息\n\n"
                + "- 告警ID：" + nullToDash(diagnosis.getAlertId()) + "\n"
                + "- 工单ID：" + nullToDash(diagnosis.getTicketId()) + "\n"
                + "- 模型：" + safeText(diagnosis.getAiModel()) + "\n"
                + "- 置信度：" + nullToDash(diagnosis.getConfidenceScore()) + "\n";
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private void syncKnowledgeToElasticsearch(OpsKnowledge knowledge, OpsTicket ticket, AiDiagnosis diagnosis) {
        try {
            OpsTicketKnowledgeDocument document = new OpsTicketKnowledgeDocument();
            document.setId("diagnosis_" + diagnosis.getId());
            document.setTicketId(diagnosis.getTicketId());
            document.setAlertId(diagnosis.getAlertId());
            document.setTitle(knowledge.getTitle());
            document.setDescription(ticket == null ? null : ticket.getDescription());
            document.setAiRootCause(diagnosis.getRootCauseAnalysis());
            document.setAiSuggestedFix(diagnosis.getSuggestedFix());
            document.setExperienceSummary(knowledge.getContentMd());
            document.setStatus(ticket == null ? null : ticket.getStatus());
            document.setHandlerUserId(ticket == null ? null : ticket.getHandlerUserId());
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
