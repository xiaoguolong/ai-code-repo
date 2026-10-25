package com.aicode.framework.followup.dto;

import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.time.Instant;
import java.util.List;

/**
 * 随访任务列表响应（Week 24）。
 *
 * @param total 条数
 * @param tasks 任务列表
 */
public record FollowUpTaskListResponse(int total, List<FollowUpTaskResponse> tasks) {

    /**
     * 由领域模型列表转换。
     *
     * @param tasks 任务列表
     * @param now   当前时间
     * @return 响应体
     */
    public static FollowUpTaskListResponse from(List<FollowUpTask> tasks, Instant now) {
        List<FollowUpTaskResponse> items = tasks == null
                ? List.of()
                : tasks.stream().map(task -> FollowUpTaskResponse.from(task, now)).toList();
        return new FollowUpTaskListResponse(items.size(), items);
    }
}
