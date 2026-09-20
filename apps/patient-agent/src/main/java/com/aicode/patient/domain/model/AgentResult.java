package com.aicode.patient.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.List;

/**
 * Agent 执行结果：最终答案 + 完整步骤轨迹 + 汇总 Token 用量 + 记忆信息。
 * 随响应返回，不落库（无状态演示）。
 */
public record AgentResult(
        String taskId,
        String sessionId,
        String answer,
        List<AgentStep> steps,
        int totalSteps,
        int recalledMemories,
        TokenUsage totalUsage,
        String model
) {
}
