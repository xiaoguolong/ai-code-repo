package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformToolDefinition;

/**
 * Tool 目录响应。
 */
public record PlatformToolResponse(
        String toolKey,
        String toolName,
        String description,
        boolean enabled,
        String registeredAt
) {

    public static PlatformToolResponse from(PlatformToolDefinition tool) {
        return new PlatformToolResponse(
                tool.toolKey(),
                tool.toolName(),
                tool.description(),
                tool.enabled(),
                tool.registeredAt().toString());
    }
}
