package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 长期记忆生命周期配置（Week 23，前缀 {@code platform.memory.lifecycle}）。
 *
 * @param ttlDays          默认保留天数；{@code <=0} 表示永不过期
 * @param cleanupEnabled   是否启用每日过期清理（默认开启）
 * @param cleanupCron      清理 Cron（6 段，Spring 语法）
 * @param rebuildBatchLimit 向量重建单批条数上限
 */
@ConfigurationProperties(prefix = "platform.memory.lifecycle")
public record PlatformMemoryLifecycleProperties(
        int ttlDays,
        boolean cleanupEnabled,
        String cleanupCron,
        int rebuildBatchLimit
) {

    /** 默认保留天数：90 天覆盖一个慢病随访周期。 */
    public static final int DEFAULT_TTL_DAYS = 90;
    /** 默认清理时间：每天 03:30。 */
    public static final String DEFAULT_CLEANUP_CRON = "0 30 3 * * *";
    /** 默认重建批量：100 条/批。 */
    public static final int DEFAULT_REBUILD_BATCH_LIMIT = 100;
    /** 重建批量硬上限：防止一次发布打爆 embedding 配额。 */
    public static final int MAX_REBUILD_BATCH_LIMIT = 500;

    /**
     * @return 有效保留天数；{@code <=0} 原样返回 0 表示永不过期
     */
    public int resolvedTtlDays() {
        return Math.max(ttlDays, 0);
    }

    /**
     * @return 有效 Cron，空白时回退 03:30
     */
    public String resolvedCleanupCron() {
        return (cleanupCron == null || cleanupCron.isBlank()) ? DEFAULT_CLEANUP_CRON : cleanupCron.trim();
    }

    /**
     * @return 有效重建批量：非法回退 100，超出 500 截断
     */
    public int resolvedRebuildBatchLimit() {
        if (rebuildBatchLimit <= 0) {
            return DEFAULT_REBUILD_BATCH_LIMIT;
        }
        return Math.min(rebuildBatchLimit, MAX_REBUILD_BATCH_LIMIT);
    }
}
