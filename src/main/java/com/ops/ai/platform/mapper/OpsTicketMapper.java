package com.ops.ai.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ops.ai.platform.entity.OpsTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpsTicketMapper extends BaseMapper<OpsTicket> {
}
