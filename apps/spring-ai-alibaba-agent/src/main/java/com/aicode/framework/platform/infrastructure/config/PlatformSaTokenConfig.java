package com.aicode.framework.platform.infrastructure.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.platform.infrastructure.security.DirectRunAuthInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 平台 Sa-Token 鉴权：Platform Run / executions 强 gate；Week 12 目录 CRUD 仍公开。
 */
@Configuration
@ConditionalOnProperty(name = "platform.security.web-interceptors.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformSaTokenConfig implements WebMvcConfigurer {

    private final PlatformSecurityProperties securityProperties;
    private final DirectRunAuthInterceptor directRunAuthInterceptor;

    public PlatformSaTokenConfig(
            PlatformSecurityProperties securityProperties,
            DirectRunAuthInterceptor directRunAuthInterceptor
    ) {
        this.securityProperties = securityProperties;
        this.directRunAuthInterceptor = directRunAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(directRunAuthInterceptor).addPathPatterns("/**");
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 白名单：除 login 与 Week 12 目录 CRUD 外，/platform/** 均需登录
            SaRouter.match("/api/v1/platform/**")
                    .notMatch("/api/v1/platform/auth/login")
                    .notMatch("/api/v1/platform/agents")
                    .notMatch("/api/v1/platform/agents/*")
                    .notMatch("/api/v1/platform/agents/*/config")
                    .notMatch("/api/v1/platform/tools")
                    .notMatch("/api/v1/platform/tools/*")
                    .notMatch("/api/v1/platform/workflows")
                    .notMatch("/api/v1/platform/workflows/*")
                    .check(r -> StpUtil.checkLogin());

            if (securityProperties.enforceDirectRuns()) {
                SaRouter.match("/api/v1/framework/agents/runs",
                                "/api/v1/workflows/patient-risk/runs",
                                "/api/v1/multi-agent/medical-assistant/runs")
                        .check(r -> StpUtil.checkLogin());
            }
        })).addPathPatterns("/**");
    }
}
