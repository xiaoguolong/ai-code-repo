package com.aicode.framework.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 注册 Workflow 请求。
 */
public record RegisterWorkflowRequest(
        @NotBlank(message = "workflowKey must not be blank") String workflowKey,
        @NotBlank(message = "name must not be blank") String name,
        String description,
        @NotBlank(message = "boundAgentKey must not be blank") String boundAgentKey
) {
}
