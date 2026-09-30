package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台 Workflow 目录条目（绑定到已注册 Agent）。
 */
public record PlatformWorkflowDefinition(
        String workflowKey,
        String name,
        String description,
        String boundAgentKey,
        boolean enabled,
        Instant registeredAt
) {
}
