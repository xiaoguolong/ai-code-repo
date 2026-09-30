package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;

/**
 * Agent 注册响应。
 */
public record PlatformAgentResponse(
        String agentKey,
        String name,
        String description,
        String agentType,
        boolean enabled,
        Integer maxIterations,
        Double temperature,
        String model,
        String registeredAt
) {

    public static PlatformAgentResponse from(PlatformAgentDefinition agent) {
        PlatformAgentConfig config = agent.config();
        return new PlatformAgentResponse(
                agent.agentKey(),
                agent.name(),
                agent.description(),
                agent.agentType().name(),
                config.enabled(),
                config.maxIterations(),
                config.temperature(),
                config.model(),
                agent.registeredAt().toString());
    }
}
