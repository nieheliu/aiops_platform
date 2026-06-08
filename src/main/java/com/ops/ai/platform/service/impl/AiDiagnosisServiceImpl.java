package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.AiDiagnosis;
import com.ops.ai.platform.mapper.AiDiagnosisMapper;
import com.ops.ai.platform.service.AiDiagnosisService;
import org.springframework.stereotype.Service;

@Service
public class AiDiagnosisServiceImpl extends ServiceImpl<AiDiagnosisMapper, AiDiagnosis> implements AiDiagnosisService {
}
