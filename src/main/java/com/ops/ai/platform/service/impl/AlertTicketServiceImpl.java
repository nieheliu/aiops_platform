package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.service.AlertTicketService;
import com.ops.ai.platform.service.DashboardCacheService;
import com.ops.ai.platform.service.OpsAlertService;
import com.ops.ai.platform.service.OpsTicketLogService;
import com.ops.ai.platform.service.OpsTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlertTicketServiceImpl implements AlertTicketService {

    private final OpsAlertService opsAlertService;
    private final OpsTicketService opsTicketService;
    private final OpsTicketLogService opsTicketLogService;
    private final DashboardCacheService dashboardCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpsTicket createTicketFromAlert(Long alertId) {
        OpsAlert alert = opsAlertService.getById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("告警不存在");
        }

        OpsTicket existing = opsTicketService.getOne(new LambdaQueryWrapper<OpsTicket>()
                .eq(OpsTicket::getAlertId, alertId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        OpsTicket ticket = new OpsTicket();
        ticket.setAlertId(alert.getId());
        ticket.setStatus(0);
        ticket.setTitle(alert.getAlertName());
        ticket.setDescription(buildDescription(alert));
        opsTicketService.save(ticket);

        saveLog(ticket.getId(), "CREATE", "由告警自动转工单");
        dashboardCacheService.evictSummary();
        return ticket;
    }

    private String buildDescription(OpsAlert alert) {
        return "告警名称：" + alert.getAlertName()
                + "\n告警等级：" + alert.getSeverity()
                + "\n实例 IP：" + alert.getInstanceIp()
                + "\n触发时间：" + alert.getTriggerTime()
                + "\n原始内容：" + alert.getRawPayload();
    }

    private void saveLog(Long ticketId, String action, String remark) {
        OpsTicketLog log = new OpsTicketLog();
        log.setTicketId(ticketId);
        log.setOperatorId(1L);
        log.setAction(action);
        log.setRemark(remark);
        log.setOperateTime(LocalDateTime.now());
        opsTicketLogService.save(log);
    }
}
