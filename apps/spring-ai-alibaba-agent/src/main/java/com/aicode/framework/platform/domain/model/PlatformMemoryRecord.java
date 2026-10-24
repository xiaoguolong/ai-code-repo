package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 长期记忆记录（Week 21）：一次已完成任务及其结论。
 *
 * <p>{@code tenantKey} + {@code userId} 是隔离谓词，落库与查询都必须带上；
 * {@code sessionId} 沿用「患者会话」语义（patientId）。</p>
 *
 * @param memoryId  记忆唯一标识
 * @param tenantKey 租户键
 * @param userId    归属用户
 * @param sessionId 会话标识（patientId）
 * @param task      任务原文
 * @param answer    结论/报告
 * @param createdAt 创建时间
 */
public record PlatformMemoryRecord(
        String memoryId,
        String tenantKey,
        long userId,
        String sessionId,
        String task,
        String answer,
        Instant createdAt
) {
}
