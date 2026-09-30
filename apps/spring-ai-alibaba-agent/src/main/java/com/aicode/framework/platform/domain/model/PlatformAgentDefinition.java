package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台 Agent 注册定义。
 */
public record PlatformAgentDefinition(
        String agentKey,
        String name,
        String description,
        AgentType agentType,
        PlatformAgentConfig config,
        Instant registeredAt
) {
}
