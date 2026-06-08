package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sys-roles")
public class SysRoleController extends BaseController<SysRole> {

    private final SysRoleService sysRoleService;

    @Override
    protected IService<SysRole> service() {
        return sysRoleService;
    }
}
