package com.aicode.framework.platform.domain.model;

/**
 * Agent 运行时配置（可覆盖默认值）。
 */
public record PlatformAgentConfig(
        boolean enabled,
        Integer maxIterations,
        Double temperature,
        String model
) {

    /** 默认启用配置。 */
    public static PlatformAgentConfig defaults() {
        return new PlatformAgentConfig(true, null, null, null);
    }
}
