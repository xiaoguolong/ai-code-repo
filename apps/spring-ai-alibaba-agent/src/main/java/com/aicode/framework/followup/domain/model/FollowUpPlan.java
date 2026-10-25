package com.aicode.framework.followup.domain.model;

import java.util.List;

/**
 * 规则生成的随访计划（Week 24）。
 *
 * @param riskLevel 风险等级（HIGH / MEDIUM / LOW）
 * @param items     任务条目（按到期时间升序）
 */
public record FollowUpPlan(String riskLevel, List<FollowUpPlanItem> items) {

    public FollowUpPlan {
        items = items == null ? List.of() : List.copyOf(items);
        riskLevel = riskLevel == null ? "LOW" : riskLevel;
    }

    /** @return 任务条数 */
    public int size() {
        return items.size();
    }
}
