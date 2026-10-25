package com.aicode.framework.followup.application;

import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.util.List;

/**
 * 一次随访 Agent 运行的结果（Week 24）。
 *
 * @param plan  规则计划 + 说明文本（未落库）
 * @param tasks 已落库的任务（无运行范围时为空列表）
 */
public record FollowUpRunResult(FollowUpPlanResult plan, List<FollowUpTask> tasks) {

    public FollowUpRunResult {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
