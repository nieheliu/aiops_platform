package com.ops.ai.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            writeUnauthorizedResponse(response, "未登录，请先登录");
            return false;
        }

        String tokenPrefix = jwtTokenProvider.getTokenPrefix();
        String bearerPrefix = tokenPrefix + " ";
        if (!authorization.startsWith(bearerPrefix)) {
            writeUnauthorizedResponse(response, "Token格式错误");
            return false;
        }

        String token = authorization.substring(bearerPrefix.length());
        try {
            jwtTokenProvider.validateToken(token);
            request.setAttribute("currentUsername", jwtTokenProvider.getUsernameFromToken(token));
            request.setAttribute("currentUserId", jwtTokenProvider.getUserIdFromToken(token));
            return true;
        } catch (Exception e) {
            writeUnauthorizedResponse(response, "Token无效或已过期");
            return false;
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
