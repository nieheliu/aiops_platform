package com.ops.ai.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ops.ai.platform.dto.CurrentUserResponse;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.entity.SysRole;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    SysUser createUser(SysUser user);

    SysUser updateUserProfile(SysUser user);

    Boolean enableUser(Long id);

    Boolean disableUser(Long id);

    Boolean resetPassword(Long id, String password);

    List<SysRole> getUserRoles(Long userId);

    Boolean assignRoles(Long userId, List<Long> roleIds);

    CurrentUserResponse getCurrentUser(String username);
}
