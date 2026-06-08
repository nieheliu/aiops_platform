package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.OpsTicketLog;
import com.ops.ai.platform.mapper.OpsTicketLogMapper;
import com.ops.ai.platform.service.OpsTicketLogService;
import org.springframework.stereotype.Service;

@Service
public class OpsTicketLogServiceImpl extends ServiceImpl<OpsTicketLogMapper, OpsTicketLog> implements OpsTicketLogService {
}
