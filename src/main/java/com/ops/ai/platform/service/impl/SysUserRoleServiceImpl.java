package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.entity.SysUserRole;
import com.ops.ai.platform.mapper.SysUserRoleMapper;
import com.ops.ai.platform.service.SysUserRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {
}
