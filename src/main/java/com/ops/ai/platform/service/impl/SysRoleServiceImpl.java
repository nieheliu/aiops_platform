package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.mapper.SysRoleMapper;
import com.ops.ai.platform.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
}
