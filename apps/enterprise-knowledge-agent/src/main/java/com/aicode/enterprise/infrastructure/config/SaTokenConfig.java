package com.aicode.enterprise.infrastructure.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 登录鉴权装配。除登录/注册与文件公开访问外，其余 /api/v1/** 一律要求登录。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler ->
                SaRouter.match("/api/v1/**")
                        .notMatch("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/files/*")
                        // Week 23：运维端点在 Sa-Token 之外，由 X-Admin-Token 守卫（fail closed）。
                        .notMatch("/api/v1/admin/**")
                        // Week 25：平台侧内部只读端点，由 X-Internal-Token 守卫（fail closed）。
                        .notMatch("/api/v1/internal/**")
                        .check(r -> StpUtil.checkLogin())
        )).addPathPatterns("/**");
    }
}
