package com.aicode.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关缓存配置（Week 19）。
 *
 * @param cacheTtlSeconds       目录缓存有效期（秒）；&lt;=0 时回落到默认 30
 * @param keyPrefix             缓存键前缀
 * @param idempotencyTtlSeconds 写入幂等键有效期（秒）；&lt;=0 时回落到默认 300
 */
@ConfigurationProperties(prefix = "gateway.catalog")
public record GatewayCatalogProperties(long cacheTtlSeconds, String keyPrefix, long idempotencyTtlSeconds) {

    /**
     * 归一化后的缓存 TTL。
     *
     * @return 正数，默认 30
     */
    public long resolvedCacheTtlSeconds() {
        return cacheTtlSeconds <= 0 ? 30L : cacheTtlSeconds;
    }

    /**
     * 归一化后的缓存键前缀。
     *
     * @return 默认 {@code gw:catalog:agents}
     */
    public String resolvedKeyPrefix() {
        return keyPrefix == null || keyPrefix.isBlank() ? "gw:catalog:agents" : keyPrefix.trim();
    }

    /**
     * 归一化后的幂等键 TTL。
     *
     * @return 正数，默认 300
     */
    public long resolvedIdempotencyTtlSeconds() {
        return idempotencyTtlSeconds <= 0 ? 300L : idempotencyTtlSeconds;
    }
}
