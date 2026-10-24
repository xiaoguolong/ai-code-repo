package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformMemoryHit;

import java.time.Instant;
import java.util.List;

/**
 * 长期记忆查询响应（Week 21）。
 *
 * @param tenantKey 查询范围租户（回显，便于确认隔离生效）
 * @param userId    查询范围用户
 * @param items     记忆条目
 */
public record PlatformMemoryResponse(String tenantKey, long userId, List<Item> items) {

    /**
     * 由领域命中项转换（score 为 0 表示「按时间列表」而非语义检索）。
     *
     * @param tenantKey 租户键
     * @param userId    用户 ID
     * @param hits      命中列表
     * @return 响应体
     */
    public static PlatformMemoryResponse from(String tenantKey, long userId, List<PlatformMemoryHit> hits) {
        return new PlatformMemoryResponse(tenantKey, userId, hits.stream()
                .map(hit -> new Item(
                        hit.record().memoryId(),
                        hit.record().sessionId(),
                        hit.record().task(),
                        hit.record().answer(),
                        hit.score() > 0 ? hit.score() : null,
                        hit.record().createdAt()))
                .toList());
    }

    /**
     * 单条记忆。
     *
     * @param memoryId  记忆 ID
     * @param patientId 会话标识（patientId）
     * @param task      任务原文
     * @param answer    结论
     * @param score     语义相似度；按时间列表时为 null
     * @param createdAt 创建时间
     */
    public record Item(String memoryId, String patientId, String task, String answer,
                       Double score, Instant createdAt) {
    }
}
