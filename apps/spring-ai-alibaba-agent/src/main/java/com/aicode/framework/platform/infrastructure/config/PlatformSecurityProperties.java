package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台安全开关。直连 Week 8–11 Run API 软 gate 默认关闭。
 */
@ConfigurationProperties(prefix = "platform.security")
public record PlatformSecurityProperties(boolean enforceDirectRuns) {

    public PlatformSecurityProperties {
        // 默认 false：直连 API 与 Week 8–11 行为一致
    }
}
