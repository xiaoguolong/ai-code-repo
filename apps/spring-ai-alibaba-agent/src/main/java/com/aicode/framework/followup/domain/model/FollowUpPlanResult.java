package com.aicode.framework.followup.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.List;

/**
 * 随访计划生成结果（Week 24，尚未落库）。
 *
 * @param patientId 患者编号
 * @param riskLevel 风险等级
 * @param planText  计划说明文本（模型生成或规则模板）
 * @param tasks     规则生成的任务条目
 * @param degraded  是否降级为规则模板
 * @param usage     token 用量
 * @param model     模型名
 * @param steps     执行轨迹
 */
public record FollowUpPlanResult(
        String patientId,
        String riskLevel,
        String planText,
        List<FollowUpPlanItem> tasks,
        boolean degraded,
        TokenUsage usage,
        String model,
        List<FollowUpStep> steps
) {

    public FollowUpPlanResult {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        steps = steps == null ? List.of() : List.copyOf(steps);
        planText = planText == null ? "" : planText;
        usage = usage == null ? TokenUsage.unknown() : usage;
        model = model == null ? "" : model;
    }
}
