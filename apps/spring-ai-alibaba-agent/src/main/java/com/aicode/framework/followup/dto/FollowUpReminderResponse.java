package com.aicode.framework.followup.dto;

import com.aicode.framework.followup.application.FollowUpReminderView;
import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 站内提醒响应（Week 24）。
 *
 * @param days    未来天数
 * @param total   提醒总数
 * @param overdue 已逾期
 * @param dueSoon 即将到期
 */
public record FollowUpReminderResponse(
        int days,
        int total,
        List<ReminderItem> overdue,
        List<ReminderItem> dueSoon
) {

    /**
     * 由提醒视图转换。
     *
     * @param view 视图
     * @param now  当前时间（算逾期天数）
     * @return 响应体
     */
    public static FollowUpReminderResponse from(FollowUpReminderView view, Instant now) {
        return new FollowUpReminderResponse(
                view.days(), view.total(),
                view.overdue().stream().map(task -> ReminderItem.from(task, now)).toList(),
                view.dueSoon().stream().map(task -> ReminderItem.from(task, now)).toList());
    }

    /**
     * 单条提醒。
     *
     * @param taskId      任务 ID
     * @param patientId   患者编号
     * @param title       标题
     * @param priority    优先级
     * @param dueAt       到期时间
     * @param overdueDays 逾期天数（未逾期为 0）
     */
    public record ReminderItem(
            String taskId,
            String patientId,
            String title,
            String priority,
            Instant dueAt,
            long overdueDays
    ) {

        static ReminderItem from(FollowUpTask task, Instant now) {
            long overdueDays = 0L;
            if (task.dueAt() != null && task.dueAt().isBefore(now)) {
                overdueDays = Math.max(0L, Duration.between(task.dueAt(), now).toDays());
            }
            return new ReminderItem(
                    task.taskId(), task.patientId(), task.title(),
                    task.priority().name(), task.dueAt(), overdueDays);
        }
    }
}
