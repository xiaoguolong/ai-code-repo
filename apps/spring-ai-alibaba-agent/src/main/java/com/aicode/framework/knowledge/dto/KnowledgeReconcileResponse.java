package com.aicode.framework.knowledge.dto;

import com.aicode.framework.platform.application.KnowledgeReconcileResult;

import java.time.Instant;

/**
 * 知识库绑定对账响应（Week 27）。
 *
 * @param tenantKey    对账范围（手动 = 调用者租户）
 * @param reconciledAt 对账时间
 * @param groups       调用企业清单次数
 * @param checked      检查的绑定条数（含失败分组）
 * @param updated      更新条数
 * @param missing      被标记 MISSING 的条数
 * @param unchanged    未变化条数
 * @param failed       失败分组内的绑定数
 * @param unbound      企业侧存在但未绑定的知识库数
 */
public record KnowledgeReconcileResponse(
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

    /**
     * 由用例结果转换。
     *
     * @param result 对账结果
     * @return 响应体
     */
    public static KnowledgeReconcileResponse from(KnowledgeReconcileResult result) {
        return new KnowledgeReconcileResponse(
                result.tenantKey(), result.reconciledAt(), result.groups(), result.checked(),
                result.updated(), result.missing(), result.unchanged(), result.failed(),
                result.unbound());
    }
}
