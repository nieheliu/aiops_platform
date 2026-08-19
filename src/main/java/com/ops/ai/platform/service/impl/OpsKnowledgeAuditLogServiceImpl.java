package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.OpsKnowledgeAuditLog;
import com.ops.ai.platform.mapper.OpsKnowledgeAuditLogMapper;
import com.ops.ai.platform.service.OpsKnowledgeAuditLogService;
import org.springframework.stereotype.Service;

@Service
public class OpsKnowledgeAuditLogServiceImpl extends ServiceImpl<OpsKnowledgeAuditLogMapper, OpsKnowledgeAuditLog> implements OpsKnowledgeAuditLogService {
}
