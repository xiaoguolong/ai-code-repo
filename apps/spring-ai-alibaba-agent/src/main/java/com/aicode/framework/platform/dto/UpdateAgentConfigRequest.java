package com.aicode.framework.platform.dto;

/**
 * 更新 Agent 配置请求。
 */
public record UpdateAgentConfigRequest(
        boolean enabled,
        Integer maxIterations,
        Double temperature,
        String model
) {
}
