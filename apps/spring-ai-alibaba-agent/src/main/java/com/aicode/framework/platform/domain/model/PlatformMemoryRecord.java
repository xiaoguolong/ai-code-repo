package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 长期记忆记录（Week 21；Week 23 增加生命周期字段）：一次已完成任务及其结论。
 *
 * <p>{@code tenantKey} + {@code userId} 是隔离谓词，落库与查询都必须带上；
 * {@code sessionId} 沿用「患者会话」语义（patientId）。</p>
 *
 * @param memoryId       记忆唯一标识
 * @param tenantKey      租户键
 * @param userId         归属用户
 * @param sessionId      会话标识（patientId）
 * @param task           任务原文
 * @param answer         结论/报告
 * @param createdAt      创建时间
 * @param expiresAt      过期时间；null = 永不过期（Week 23）
 * @param embeddingModel 向量来源标识 {@code provider:model:dimension}；null = 未知/待重建（Week 23）
 */
public record PlatformMemoryRecord(
        String memoryId,
        String tenantKey,
        long userId,
        String sessionId,
        String task,
        String answer,
        Instant createdAt,
        Instant expiresAt,
        String embeddingModel
) {

    /**
     * Week 21 兼容构造器：不设置 TTL 与向量来源（既有调用方/测试无需改动）。
     *
     * @param memoryId  记忆 ID
     * @param tenantKey 租户
     * @param userId    用户
     * @param sessionId 会话
     * @param task      任务
     * @param answer    结论
     * @param createdAt 创建时间
     */
    public PlatformMemoryRecord(
            String memoryId,
            String tenantKey,
            long userId,
            String sessionId,
            String task,
            String answer,
            Instant createdAt
    ) {
        this(memoryId, tenantKey, userId, sessionId, task, answer, createdAt, null, null);
    }

    /**
     * 是否在给定时间点已过期。
     *
     * @param now 判断基准时间
     * @return true = 已过期（expiresAt 非空且不晚于 now）
     */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
