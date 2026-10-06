package com.aicode.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关会话配置（Week 19）。
 *
 * @param tokenName  对外会话头名（兼容既有客户端习惯的 {@code satoken} 头）
 * @param ttlSeconds 会话有效期（秒）；&lt;=0 时回落到默认 1800
 * @param storage    存储实现标识（自检展示用）
 */
@ConfigurationProperties(prefix = "gateway.session")
public record GatewaySessionProperties(String tokenName, long ttlSeconds, String storage) {

    /**
     * 归一化后的会话头名。
     *
     * @return 非空头名，默认 {@code satoken}
     */
    public String resolvedTokenName() {
        return tokenName == null || tokenName.isBlank() ? "satoken" : tokenName.trim();
    }

    /**
     * 归一化后的有效期（秒）。
     *
     * @return 正数，默认 1800
     */
    public long resolvedTtlSeconds() {
        return ttlSeconds <= 0 ? 1800L : ttlSeconds;
    }

    /**
     * 归一化后的存储标识。
     *
     * @return 默认 {@code redis}
     */
    public String resolvedStorage() {
        return storage == null || storage.isBlank() ? "redis" : storage.trim();
    }
}
