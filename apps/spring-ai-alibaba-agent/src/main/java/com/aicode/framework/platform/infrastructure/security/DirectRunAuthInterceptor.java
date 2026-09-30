package com.aicode.framework.platform.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 直连 Run API 软 gate 的 Agent 权限校验（需 {@code enforce-direct-runs=true} 且已登录）。
 */
public class DirectRunAuthInterceptor implements HandlerInterceptor {

    private static final Map<String, String> PATH_TO_AGENT = Map.of(
            "/api/v1/framework/agents/runs", "framework-react",
            "/api/v1/workflows/patient-risk/runs", "patient-risk",
            "/api/v1/multi-agent/medical-assistant/runs", "medical-assistant"
    );

    private final PlatformSecurityProperties securityProperties;
    private final PlatformPermissionChecker permissionChecker;

    public DirectRunAuthInterceptor(
            PlatformSecurityProperties securityProperties,
            PlatformPermissionChecker permissionChecker
    ) {
        this.securityProperties = securityProperties;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!securityProperties.enforceDirectRuns() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String agentKey = PATH_TO_AGENT.get(request.getRequestURI());
        if (agentKey == null) {
            return true;
        }
        StpUtil.checkLogin();
        permissionChecker.requireAgentRun(StpUtil.getLoginIdAsLong(), agentKey);
        return true;
    }
}
