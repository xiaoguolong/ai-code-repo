package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台 Tool 目录条目（元数据；运行时仍由 {@code ToolPort} 执行）。
 */
public record PlatformToolDefinition(
        String toolKey,
        String toolName,
        String description,
        boolean enabled,
        Instant registeredAt
) {
}
