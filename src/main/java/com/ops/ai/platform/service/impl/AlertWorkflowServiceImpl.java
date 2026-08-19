package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.service.AiDiagnosisWorkflowService;
import com.ops.ai.platform.service.AiModelService;
import com.ops.ai.platform.service.AlertWorkflowService;
import com.ops.ai.platform.service.OpsAlertService;
import com.ops.ai.platform.service.OpsTicketLogService;
import com.ops.ai.platform.service.OpsTicketService;
import com.ops.ai.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertWorkflowServiceImpl implements AlertWorkflowService {

    private static final int TICKET_STATUS_PENDING = 0;

    private static final String SYSTEM_USERNAME = "system";

    private final OpsAlertService opsAlertService;

    private final OpsTicketService opsTicketService;

    private final OpsTicketLogService opsTicketLogService;

    private final SysUserService sysUserService;

    private final AiDiagnosisWorkflowService aiDiagnosisWorkflowService;

    private final AiModelService aiModelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTicketFromAlert(Long alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId must not be null");
        }

        OpsAlert alert = opsAlertService.getById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found, alertId=" + alertId);
        }

        OpsTicket existingTicket = opsTicketService.getOne(
                new LambdaQueryWrapper<OpsTicket>()
                        .eq(OpsTicket::getAlertId, alertId)
                        .last("LIMIT 1")
        );
        if (existingTicket != null) {
            log.info("Ticket already exists for alertId={}, ticketId={}", alertId, existingTicket.getId());
            return;
        }

        OpsTicket ticket = new OpsTicket();
        ticket.setAlertId(alert.getId());
        ticket.setStatus(TICKET_STATUS_PENDING);
        ticket.setTitle(buildTicketTitle(alert));
        ticket.setDescription(buildTicketDescription(alert));
        opsTicketService.save(ticket);

        saveCreateLogIfSystemUserExists(ticket.getId());
        diagnoseAlertTicket(alert.getId(), ticket.getId());
    }

    private void diagnoseAlertTicket(Long alertId, Long ticketId) {
        try {
            aiDiagnosisWorkflowService.diagnoseAlertTicket(alertId, ticketId, aiModelService.getDefaultModelId());
        } catch (Exception e) {
            log.warn("AI diagnosis failed, alertId={}, ticketId={}", alertId, ticketId, e);
        }
    }

    private String buildTicketTitle(OpsAlert alert) {
        return "[P" + alert.getSeverity() + "] " + alert.getAlertName() + " - " + alert.getInstanceIp();
    }

    private String buildTicketDescription(OpsAlert alert) {
        return "系统根据告警自动创建工单。\n"
                + "告警ID：" + alert.getId() + "\n"
                + "告警名称：" + alert.getAlertName() + "\n"
                + "严重等级：" + alert.getSeverity() + "\n"
                + "实例IP：" + alert.getInstanceIp() + "\n"
                + "触发时间：" + alert.getTriggerTime();
    }

    private void saveCreateLogIfSystemUserExists(Long ticketId) {
        SysUser systemUser = sysUserService.getOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, SYSTEM_USERNAME)
                        .last("LIMIT 1")
        );
        if (systemUser == null) {
            log.warn("Skip ticket create log because system user does not exist, username={}", SYSTEM_USERNAME);
            return;
        }

        OpsTicketLog ticketLog = new OpsTicketLog();
        ticketLog.setTicketId(ticketId);
        ticketLog.setOperatorId(systemUser.getId());
        ticketLog.setAction("CREATE");
        ticketLog.setRemark("系统根据告警自动创建工单");
        ticketLog.setOperateTime(LocalDateTime.now());
        opsTicketLogService.save(ticketLog);
    }
}
