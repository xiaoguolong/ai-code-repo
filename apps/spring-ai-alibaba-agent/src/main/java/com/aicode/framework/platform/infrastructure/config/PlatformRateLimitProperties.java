package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录/身份校验限流配置（Week 22，前缀 {@code platform.security.rate-limit}）。
 *
 * <p>默认值写在 {@code application.yml}：`enabled=true`、5 次 / 5 分钟窗口、封禁 5 分钟。
 * 本类只做「非法值回退」，避免配置写错时把限流关掉或算出负数窗口。</p>
 *
 * @param enabled           总开关；false 时完全回到 Week 21 行为（本地联调常用）
 * @param maxAttempts       窗口内允许的失败次数
 * @param windowSeconds     失败计数窗口（秒）
 * @param blockSeconds      达到阈值后的封禁时长（秒）
 * @param trustForwardedFor 是否信任 {@code X-Forwarded-For}（只有前面确实有可信反代时才开）
 */
@ConfigurationProperties(prefix = "platform.security.rate-limit")
public record PlatformRateLimitProperties(
        boolean enabled,
        int maxAttempts,
        int windowSeconds,
        int blockSeconds,
        boolean trustForwardedFor
) {

    /** 默认允许失败次数。 */
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    /** 默认窗口秒数。 */
    public static final int DEFAULT_WINDOW_SECONDS = 300;
    /** 默认封禁秒数。 */
    public static final int DEFAULT_BLOCK_SECONDS = 300;

    /**
     * @return 有效失败次数上限，非法值回退 5
     */
    public int resolvedMaxAttempts() {
        return maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
    }

    /**
     * @return 有效窗口秒数，非法值回退 300
     */
    public int resolvedWindowSeconds() {
        return windowSeconds <= 0 ? DEFAULT_WINDOW_SECONDS : windowSeconds;
    }

    /**
     * @return 有效封禁秒数，非法值回退 300
     */
    public int resolvedBlockSeconds() {
        return blockSeconds <= 0 ? DEFAULT_BLOCK_SECONDS : blockSeconds;
    }
}
