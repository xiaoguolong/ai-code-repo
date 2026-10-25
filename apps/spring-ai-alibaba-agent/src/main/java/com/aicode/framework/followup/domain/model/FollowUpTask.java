package com.aicode.framework.followup.domain.model;

import java.time.Instant;

/**
 * 随访任务（Week 24）。
 *
 * <p>不存患者姓名/诊断：任务只带 {@code patientId} 与计划标题，患者信息按需查
 * （与审批表「不冗余报告正文」同一理由：避免同一份数据两处不一致）。</p>
 *
 * @param taskId      任务 ID
 * @param tenantKey   归属租户（由用户记录推导）
 * @param userId      任务归属人（发起医生）
 * @param patientId   患者编号
 * @param source      来源（APPROVAL / AGENT）
 * @param approvalId  来源审批 ID；Agent 直跑时为 null
 * @param executionId 来源执行 ID；可空
 * @param title       任务标题
 * @param detail      任务说明
 * @param priority    优先级
 * @param status      状态
 * @param dueAt       到期时间
 * @param createdAt   创建时间
 * @param updatedAt   最近更新时间
 * @param completedAt 完成时间；未完成/已取消时为 null
 * @param note        完成/取消备注
 */
public record FollowUpTask(
        String taskId,
        String tenantKey,
        long userId,
        String patientId,
        FollowUpSource source,
        String approvalId,
        String executionId,
        String title,
        String detail,
        FollowUpPriority priority,
        FollowUpStatus status,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String note
) {

    public FollowUpTask {
        detail = detail == null ? "" : detail;
        note = note == null ? "" : note;
        title = title == null ? "" : title;
        patientId = patientId == null ? "" : patientId;
    }

    /**
     * @return 是否仍可被完成/取消
     */
    public boolean pending() {
        return status == FollowUpStatus.PENDING;
    }
}
