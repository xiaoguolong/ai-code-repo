package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.PlatformApproval;

import java.time.Instant;
import java.util.List;

/**
 * 审批待办超时提醒视图（Week 27，只读）。
 *
 * <p>与随访站内提醒同构：直接查「PENDING 且超过阈值」，不依赖调度是否跑过。
 * {@code overdueHours} 是距创建时间的整小时数（向下取整）。</p>
 *
 * @param thresholdHours 生效的超时阈值（小时）
 * @param evaluatedAt    视图生成时间
 * @param items          超时待办（最久的在前）
 */
public record ApprovalReminderView(
        int thresholdHours,
        Instant evaluatedAt,
        List<Item> items
) {

    public ApprovalReminderView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 一条超时待办。
     *
     * @param approval     审批任务（不返回报告正文）
     * @param overdueHours 已超时小时数
     */
    public record Item(PlatformApproval approval, long overdueHours) {
    }
}
