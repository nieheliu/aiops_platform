package com.ops.ai.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ops.ai.platform.common.BaseController;
import com.ops.ai.platform.dto.PasswordResetRequest;
import com.ops.ai.platform.dto.UserRoleAssignRequest;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sys-users")
public class SysUserController extends BaseController<SysUser> {

    private final SysUserService sysUserService;

    @Override
    protected IService<SysUser> service() {
        return sysUserService;
    }

    @Override
    @PostMapping
    public Boolean create(@RequestBody SysUser entity) {
        sysUserService.createUser(entity);
        return true;
    }

    @Override
    @PutMapping
    public Boolean update(@RequestBody SysUser entity) {
        sysUserService.updateUserProfile(entity);
        return true;
    }

    @Override
    @GetMapping("/{id}")
    public SysUser getById(@PathVariable java.io.Serializable id) {
        return hidePassword(sysUserService.getById(id));
    }

    @Override
    @GetMapping
    public List<SysUser> list() {
        return sysUserService.list().stream().map(this::hidePassword).toList();
    }

    @Override
    @GetMapping("/page")
    public IPage<SysUser> page(@RequestParam(defaultValue = "1") long current,
                               @RequestParam(defaultValue = "10") long size) {
        IPage<SysUser> page = sysUserService.page(Page.of(current, size));
        page.getRecords().forEach(this::hidePassword);
        return page;
    }

    @PostMapping("/{id}/enable")
    public Boolean enable(@PathVariable Long id) {
        return sysUserService.enableUser(id);
    }

    @Override
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Serializable id) {
        return sysUserService.deleteUserSafely(Long.valueOf(id.toString()));
    }

    @GetMapping("/enabled-options")
    public List<com.ops.ai.platform.dto.UserOptionResponse> enabledOptions() {
        return sysUserService.listEnabledUserOptions();
    }

    @PostMapping("/{id}/disable")
    public Boolean disable(@PathVariable Long id) {
        return sysUserService.disableUserSafely(id);
    }

    @PostMapping("/{id}/reset-password")
    public Boolean resetPassword(@PathVariable Long id, @RequestBody PasswordResetRequest request) {
        return sysUserService.resetPassword(id, request == null ? null : request.getPassword());
    }

    @GetMapping("/{id}/roles")
    public List<SysRole> roles(@PathVariable Long id) {
        return sysUserService.getUserRoles(id);
    }

    @PutMapping("/{id}/roles")
    public Boolean assignRoles(@PathVariable Long id, @RequestBody UserRoleAssignRequest request) {
        return sysUserService.assignRoles(id, request == null ? null : request.getRoleIds());
    }

    private SysUser hidePassword(SysUser user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
