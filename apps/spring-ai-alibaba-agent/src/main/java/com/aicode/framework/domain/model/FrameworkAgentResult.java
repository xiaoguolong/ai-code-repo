package com.aicode.framework.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.List;

/**
 * Graph Agent 运行结果：最终答案 + 工具轨迹 + 汇总 Token + 模型名。
 *
 * @param taskId     任务标识
 * @param answer     最终答案
 * @param steps      工具调用轨迹
 * @param totalSteps 工具调用步数
 * @param usage      多轮汇总 Token 用量
 * @param model      实际使用的模型名
 */
public record FrameworkAgentResult(
        String taskId,
        String answer,
        List<FrameworkAgentStep> steps,
        int totalSteps,
        TokenUsage usage,
        String model
) {

    public FrameworkAgentResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
        usage = usage == null ? TokenUsage.unknown() : usage;
    }
}
