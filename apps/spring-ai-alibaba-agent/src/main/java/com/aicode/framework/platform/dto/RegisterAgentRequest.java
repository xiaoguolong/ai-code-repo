package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 注册 Agent 请求。
 */
public record RegisterAgentRequest(
        @NotBlank(message = "agentKey must not be blank") String agentKey,
        @NotBlank(message = "name must not be blank") String name,
        String description,
        @NotNull(message = "agentType must not be null") AgentType agentType
) {
}
