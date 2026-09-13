package com.aicode.patient.domain.model;

import java.util.List;

/**
 * Agent 执行结果：最终答案 + 完整步骤轨迹 + 汇总 Token 用量。随响应返回，不落库（无状态演示）。
 */
public record AgentResult(
        String taskId,
        String answer,
        List<AgentStep> steps,
        int totalSteps,
        TokenUsage totalUsage,
        String model
) {
}
