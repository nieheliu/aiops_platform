package com.ops.ai.platform.config;

import com.ops.ai.platform.security.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/api/alerts/webhook",
                        "/error",
                        "/favicon.ico",
                        "/login.html",
                        "/*.html",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
