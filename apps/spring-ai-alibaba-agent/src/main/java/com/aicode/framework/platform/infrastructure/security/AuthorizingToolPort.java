package com.aicode.framework.platform.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformSecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * {@link ToolPort} 装饰器：仅在 Platform Run 或直连强 gate 时校验 Tool + patientId；
 * 软 gate 关闭时的直连 Run 与 Week 8–11 一致（不因 incidental 登录态拦截）。
 */
public class AuthorizingToolPort implements ToolPort {

    private final ToolPort delegate;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public AuthorizingToolPort(
            ToolPort delegate,
            PlatformPermissionChecker permissionChecker,
            PlatformSecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.permissionChecker = permissionChecker;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolDefinition> definitions() {
        return delegate.definitions();
    }

    @Override
    public ToolResult execute(ToolCall call) {
        Long userId = resolveUserIdForAuthorization();
        if (userId != null) {
            permissionChecker.requireToolExecute(userId, call.name(), parseArguments(call.arguments()));
        }
        return delegate.execute(call);
    }

    /**
     * Platform Run 始终校验；直连 Run 仅 {@code enforce-direct-runs=true} 时校验；否则放行（Week 8–11 兼容）。
     */
    private Long resolveUserIdForAuthorization() {
        if (PlatformSecurityContext.isPlatformRun()) {
            return PlatformSecurityContext.currentPlatformUserId();
        }
        if (!securityProperties.enforceDirectRuns()) {
            return null;
        }
        return resolveLoginUserId();
    }

    private Long resolveLoginUserId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception ignored) {
            // 单测或未装配 Sa-Token 时不抛错
        }
        return null;
    }

    /** 直连强 gate 且无登录态时拒绝 Tool。 */
    public void requireLoginForDirectRun() {
        if (securityProperties.enforceDirectRuns() && resolveLoginUserId() == null) {
            throw new ToolExecutionException("tool execution requires login when enforce-direct-runs is enabled");
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
