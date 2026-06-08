package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.OpsKnowledge;
import com.ops.ai.platform.service.OpsKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops-knowledge")
public class OpsKnowledgeController extends BaseController<OpsKnowledge> {

    private final OpsKnowledgeService opsKnowledgeService;

    @Override
    protected IService<OpsKnowledge> service() {
        return opsKnowledgeService;
    }
}
