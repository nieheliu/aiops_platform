package com.ops.ai.platform.service.impl;

import com.ops.ai.platform.dto.LoginRequest;
import com.ops.ai.platform.dto.LoginResponse;
import com.ops.ai.platform.entity.SysUser;
import com.ops.ai.platform.security.JwtTokenProvider;
import com.ops.ai.platform.service.AuthService;
import com.ops.ai.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserService sysUserService;

    private final JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername())
                .one();

        if (user == null || !matchesPassword(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("用户已被禁用");
        }

        String token = jwtTokenProvider.generateToken(user);
        return new LoginResponse(
                token,
                jwtTokenProvider.getTokenPrefix(),
                jwtTokenProvider.getExpirationInSeconds(),
                user.getId(),
                user.getUsername()
        );
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }
}
