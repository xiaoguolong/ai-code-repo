package com.aicode.framework.followup.domain.model;

/**
 * 随访计划条目（Week 24，尚未落库）。
 *
 * @param title    标题
 * @param detail   说明
 * @param priority 优先级
 * @param dueAt    到期时间
 */
public record FollowUpPlanItem(String title, String detail, FollowUpPriority priority, java.time.Instant dueAt) {

    public FollowUpPlanItem {
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
    }
}
