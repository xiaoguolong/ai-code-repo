package com.aicode.framework.platform.application;

import java.time.Instant;

/**
 * 知识库绑定对账结果（Week 27）。
 *
 * <p>{@code checked} 包含 {@code failed}（失败分组里的绑定也检查过，只是没拿到企业清单）；
 * {@code unbound} 只统计不建绑定：是否把企业新库纳入租户可见范围仍是管理动作。</p>
 *
 * @param tenantKey   对账范围（手动 = 调用者租户；调度 = 空串表示全部）
 * @param reconciledAt 对账时间
 * @param groups      实际调用企业清单的次数（= 不同 {@code enterpriseUserId} 数）
 * @param checked     本次检查的绑定条数
 * @param updated     元数据/状态发生变化并落库的条数
 * @param missing     本次被标记 {@code MISSING} 的条数（不删除行）
 * @param unchanged   企业侧存在且元数据未变的条数（仍刷新 {@code lastSyncedAt}）
 * @param failed      企业清单调用失败分组内的绑定数（状态保持不变）
 * @param unbound     企业侧存在但本租户未绑定的知识库数
 */
public record KnowledgeReconcileResult(
        String tenantKey,
        Instant reconciledAt,
        int groups,
        int checked,
        int updated,
        int missing,
        int unchanged,
        int failed,
        int unbound
) {

    public KnowledgeReconcileResult {
        tenantKey = tenantKey == null ? "" : tenantKey;
    }

    /**
     * 空结果（对账关闭 / 无绑定）。
     *
     * @param tenantKey 对账范围
     * @param at        对账时间
     * @return 全 0 结果
     */
    public static KnowledgeReconcileResult empty(String tenantKey, Instant at) {
        return new KnowledgeReconcileResult(tenantKey, at, 0, 0, 0, 0, 0, 0, 0);
    }
}
