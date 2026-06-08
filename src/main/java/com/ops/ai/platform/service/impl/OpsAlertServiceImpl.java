package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.OpsAlert;
import com.ops.ai.platform.mapper.OpsAlertMapper;
import com.ops.ai.platform.service.OpsAlertService;
import org.springframework.stereotype.Service;

@Service
public class OpsAlertServiceImpl extends ServiceImpl<OpsAlertMapper, OpsAlert> implements OpsAlertService {
}
