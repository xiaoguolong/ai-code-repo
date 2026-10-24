package com.aicode.enterprise.infrastructure.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一身份（SSO）配置（Week 21，前缀 {@code sso}）。
 *
 * @param enabled         总开关；false 时 enterprise 完全走第 4 周本地校验
 * @param platformBaseUrl 平台根地址（不含路径），如 {@code http://localhost:8084}
 * @param timeoutMs       单次校验超时（毫秒）
 * @param autoProvision   首次登录是否自动创建本地用户并建链；关闭则要求管理员先建链
 */
@ConfigurationProperties(prefix = "sso")
public record SsoProperties(
        boolean enabled,
        String platformBaseUrl,
        int timeoutMs,
        boolean autoProvision
) {

    /** 默认平台地址（与 platform 8084 一致）。 */
    public static final String DEFAULT_PLATFORM_BASE_URL = "http://localhost:8084";
    /** 默认超时毫秒。 */
    public static final int DEFAULT_TIMEOUT_MS = 3000;

    public SsoProperties {
        platformBaseUrl = normalizeBaseUrl(platformBaseUrl);
        timeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PLATFORM_BASE_URL;
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
