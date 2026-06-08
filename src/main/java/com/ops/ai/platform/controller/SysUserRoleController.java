package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.SysUserRole;
import com.ops.ai.platform.service.SysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sys-user-roles")
public class SysUserRoleController extends BaseController<SysUserRole> {

    private final SysUserRoleService sysUserRoleService;

    @Override
    protected IService<SysUserRole> service() {
        return sysUserRoleService;
    }
}
