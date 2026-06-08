package com.ops.ai.platform.service;

import com.ops.ai.platform.entity.OpsTicket;

public interface AlertTicketService {

    OpsTicket createTicketFromAlert(Long alertId);
}
