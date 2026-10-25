package com.aicode.framework.followup.dto;

import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.time.Instant;

/**
 * 随访任务响应（Week 24）。
 *
 * @param taskId      任务 ID
 * @param tenantKey   归属租户
 * @param userId      归属用户
 * @param patientId   患者编号
 * @param source      来源（APPROVAL / AGENT）
 * @param approvalId  来源审批
 * @param executionId 来源执行
 * @param title       标题
 * @param detail      说明
 * @param priority    优先级
 * @param status      状态
 * @param dueAt       到期时间
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param completedAt 完成时间
 * @param note        备注
 * @param overdue     是否已逾期（仅 PENDING 且有到期时间时可能为 true）
 */
public record FollowUpTaskResponse(
        String taskId,
        String tenantKey,
        long userId,
        String patientId,
        String source,
        String approvalId,
        String executionId,
        String title,
        String detail,
        String priority,
        String status,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String note,
        boolean overdue
) {

    /**
     * 由领域模型转换。
     *
     * @param task 任务
     * @param now  当前时间（判断逾期用）
     * @return 响应体
     */
    public static FollowUpTaskResponse from(FollowUpTask task, Instant now) {
        return new FollowUpTaskResponse(
                task.taskId(), task.tenantKey(), task.userId(), task.patientId(),
                task.source().name(), task.approvalId(), task.executionId(),
                task.title(), task.detail(), task.priority().name(), task.status().name(),
                task.dueAt(), task.createdAt(), task.updatedAt(), task.completedAt(), task.note(),
                task.pending() && task.dueAt() != null && task.dueAt().isBefore(now));
    }
}
