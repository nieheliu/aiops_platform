package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.OpsTicket;
import com.ops.ai.platform.mapper.OpsTicketMapper;
import com.ops.ai.platform.service.OpsTicketService;
import org.springframework.stereotype.Service;

@Service
public class OpsTicketServiceImpl extends ServiceImpl<OpsTicketMapper, OpsTicket> implements OpsTicketService {
}
