package com.ops.ai.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ops.ai.platform.dto.CurrentUserResponse;
import com.ops.ai.platform.entity.SysRole;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.entity.SysUserRole;
import com.ops.ai.platform.mapper.SysUserMapper;
import com.ops.ai.platform.service.SysRoleService;
import com.ops.ai.platform.service.SysUserRoleService;
import com.ops.ai.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final int STATUS_ENABLED = 1;

    private static final int STATUS_DISABLED = 0;

    private final SysRoleService sysRoleService;

    private final SysUserRoleService sysUserRoleService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(SysUser user) {
        validateUserForCreate(user);
        ensureUsernameUnique(user.getUsername(), null);
        user.setEmail(normalizeOptionalText(user.getEmail()));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(STATUS_ENABLED);
        }
        save(user);
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser updateUserProfile(SysUser user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        SysUser existing = getById(user.getId());
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (StringUtils.hasText(user.getUsername())) {
            ensureUsernameUnique(user.getUsername(), user.getId());
            existing.setUsername(user.getUsername());
        }
        existing.setEmail(normalizeOptionalText(user.getEmail()));
        existing.setStatus(user.getStatus());
        updateById(existing);
        existing.setPassword(null);
        return existing;
    }

    @Override
    public Boolean enableUser(Long id) {
        return updateUserStatus(id, STATUS_ENABLED);
    }

    @Override
    public Boolean disableUser(Long id) {
        return updateUserStatus(id, STATUS_DISABLED);
    }

    @Override
    public Boolean resetPassword(Long id, String password) {
        if (id == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        SysUser user = getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(password));
        return updateById(user);
    }

    @Override
    public List<SysRole> getUserRoles(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<Long> roleIds = sysUserRoleService.list(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysRoleService.listByIds(roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null || getById(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        sysUserRoleService.remove(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(roleIds)) {
            return true;
        }
        List<SysUserRole> userRoles = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds.stream().distinct().toList()) {
            if (roleId == null || sysRoleService.getById(roleId) == null) {
                continue;
            }
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setGrantTime(now);
            userRoles.add(userRole);
        }
        return userRoles.isEmpty() || sysUserRoleService.saveBatch(userRoles);
    }

    @Override
    public CurrentUserResponse getCurrentUser(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("当前用户为空");
        }
        SysUser user = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<String> roles = getUserRoles(user.getId()).stream()
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .toList();

        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setStatus(user.getStatus());
        response.setRoles(roles);
        response.setPermissions(buildPermissions(roles));
        return response;
    }

    private void validateUserForCreate(SysUser user) {
        if (user == null || !StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
    }

    private void ensureUsernameUnique(String username, Long excludeUserId) {
        SysUser existing = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (existing != null && (excludeUserId == null || !existing.getId().equals(excludeUserId))) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Boolean updateUserStatus(Long id, Integer status) {
        if (id == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        SysUser user = getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setStatus(status);
        return updateById(user);
    }

    private List<String> buildPermissions(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return List.of("dashboard:view", "alert:manage", "ticket:manage", "diagnosis:manage", "knowledge:manage", "system:user", "system:role");
        }
        if (roles.contains("OPS")) {
            return List.of("dashboard:view", "alert:manage", "ticket:manage", "diagnosis:manage", "knowledge:manage");
        }
        return List.of("dashboard:view", "alert:view", "diagnosis:view", "knowledge:view");
    }
}
