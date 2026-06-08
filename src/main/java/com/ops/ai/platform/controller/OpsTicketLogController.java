package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.service.OpsTicketLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops-ticket-logs")
public class OpsTicketLogController extends BaseController<OpsTicketLog> {

    private final OpsTicketLogService opsTicketLogService;

    @Override
    protected IService<OpsTicketLog> service() {
        return opsTicketLogService;
    }
}
