package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;

/**
 * Workflow 目录响应。
 */
public record PlatformWorkflowResponse(
        String workflowKey,
        String name,
        String description,
        String boundAgentKey,
        boolean enabled,
        String registeredAt
) {

    public static PlatformWorkflowResponse from(PlatformWorkflowDefinition workflow) {
        return new PlatformWorkflowResponse(
                workflow.workflowKey(),
                workflow.name(),
                workflow.description(),
                workflow.boundAgentKey(),
                workflow.enabled(),
                workflow.registeredAt().toString());
    }
}
