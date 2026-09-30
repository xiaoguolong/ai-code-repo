package com.aicode.framework.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 注册 Tool 请求。
 */
public record RegisterToolRequest(
        @NotBlank(message = "toolKey must not be blank") String toolKey,
        @NotBlank(message = "toolName must not be blank") String toolName,
        String description
) {
}
