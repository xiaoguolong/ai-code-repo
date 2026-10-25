package com.aicode.framework.followup.application;

import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.util.List;

/**
 * 站内提醒视图（Week 24）：逾期 + 未来 N 天内到期。
 *
 * @param days    未来天数
 * @param overdue 已逾期任务（按到期升序）
 * @param dueSoon 未来 N 天内到期任务（按到期升序）
 */
public record FollowUpReminderView(int days, List<FollowUpTask> overdue, List<FollowUpTask> dueSoon) {

    public FollowUpReminderView {
        overdue = overdue == null ? List.of() : List.copyOf(overdue);
        dueSoon = dueSoon == null ? List.of() : List.copyOf(dueSoon);
    }

    /** @return 提醒总数（逾期 + 即将到期） */
    public int total() {
        return overdue.size() + dueSoon.size();
    }
}
