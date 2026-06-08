package com.ops.ai.platform.controller;

import com.ops.ai.platform.dto.CurrentUserResponse;
import com.ops.ai.platform.dto.LoginRequest;
import com.ops.ai.platform.dto.LoginResponse;
import com.ops.ai.platform.service.AuthService;
import com.ops.ai.platform.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    private final SysUserService sysUserService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(HttpServletRequest request) {
        return sysUserService.getCurrentUser((String) request.getAttribute("currentUsername"));
    }
}
