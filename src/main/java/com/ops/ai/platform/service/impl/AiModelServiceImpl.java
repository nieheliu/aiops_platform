package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.config.AiOpsModelProperties;
import com.ops.ai.platform.dto.AiModelOption;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.service.AiDiagnosisService;
import com.ops.ai.platform.service.AiModelService;
import com.ops.ai.platform.service.OpsTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final AiOpsModelProperties properties;

    private final AiDiagnosisService aiDiagnosisService;

    private final OpsTicketService opsTicketService;

    @Override
    public String getDefaultModelId() {
        return properties.getDefaultModelId();
    }

    @Override
    public AiOpsModelProperties.ModelDefinition requireModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalArgumentException("请选择诊断模型");
        }
        return properties.getModels().stream()
                .filter(model -> modelId.equals(model.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型: " + modelId));
    }

    @Override
    public List<AiModelOption> listAll() {
        Set<String> usedModelIds = new HashSet<>();
        return toOptions(usedModelIds);
    }

    @Override
    public List<AiModelOption> listAvailable(Long alertId, Long ticketId) {
        Set<String> usedModelIds = new HashSet<>(listUsedModelIds(alertId, ticketId));
        return toOptions(usedModelIds);
    }

    @Override
    public void ensureModelNotUsed(Long alertId, Long ticketId, String modelId) {
        AiOpsModelProperties.ModelDefinition definition = requireModel(modelId);
        if (findExistingDiagnosis(alertId, ticketId, definition.getId()) != null) {
            throw new IllegalArgumentException("模型 " + definition.getName() + " 已用于当前告警/工单，请更换其他模型");
        }
    }

    @Override
    public AiDiagnosis findExistingDiagnosis(Long alertId, Long ticketId, String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        LambdaQueryWrapper<AiDiagnosis> wrapper = buildDiagnosisScopeWrapper(alertId, ticketId);
        if (wrapper == null) {
            return null;
        }
        wrapper.eq(AiDiagnosis::getAiModel, modelId)
                .orderByDesc(AiDiagnosis::getCreateTime)
                .last("LIMIT 1");
        return aiDiagnosisService.getOne(wrapper);
    }

    private List<AiModelOption> toOptions(Set<String> usedModelIds) {
        return properties.getModels().stream().map(definition -> {
            AiModelOption option = new AiModelOption();
            option.setId(definition.getId());
            option.setName(definition.getName());
            option.setProvider(definition.getProvider());
            option.setModel(definition.getModel());
            option.setFree(definition.isFree());
            boolean used = usedModelIds.contains(definition.getId());
            option.setUsed(used);
            option.setAvailable(!used);
            return option;
        }).toList();
    }

    private LambdaQueryWrapper<AiDiagnosis> buildDiagnosisScopeWrapper(Long alertId, Long ticketId) {
        LambdaQueryWrapper<AiDiagnosis> wrapper = new LambdaQueryWrapper<>();
        if (ticketId != null) {
            wrapper.eq(AiDiagnosis::getTicketId, ticketId);
            return wrapper;
        }
        if (alertId != null) {
            OpsTicket ticket = opsTicketService.getOne(new LambdaQueryWrapper<OpsTicket>()
                    .eq(OpsTicket::getAlertId, alertId)
                    .orderByDesc(OpsTicket::getCreateTime)
                    .last("LIMIT 1"));
            if (ticket != null) {
                Long linkedTicketId = ticket.getId();
                wrapper.and(query -> query.eq(AiDiagnosis::getAlertId, alertId)
                        .or()
                        .eq(AiDiagnosis::getTicketId, linkedTicketId));
            } else {
                wrapper.eq(AiDiagnosis::getAlertId, alertId);
            }
            return wrapper;
        }
        return null;
    }

    private List<String> listUsedModelIds(Long alertId, Long ticketId) {
        LambdaQueryWrapper<AiDiagnosis> wrapper = buildDiagnosisScopeWrapper(alertId, ticketId);
        if (wrapper == null) {
            return List.of();
        }
        return aiDiagnosisService.list(wrapper).stream()
                .map(AiDiagnosis::getAiModel)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
