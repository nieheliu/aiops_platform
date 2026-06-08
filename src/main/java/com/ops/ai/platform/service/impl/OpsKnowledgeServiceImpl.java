package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.mapper.OpsKnowledgeMapper;
import com.ops.ai.platform.service.OpsKnowledgeService;
import org.springframework.stereotype.Service;

@Service
public class OpsKnowledgeServiceImpl extends ServiceImpl<OpsKnowledgeMapper, OpsKnowledge> implements OpsKnowledgeService {
}
