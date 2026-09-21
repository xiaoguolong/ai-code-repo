package com.aicode.framework.dto;

import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.domain.model.FrameworkAgentStep;

import java.util.List;

/**
 * Graph Agent 运行成功体：最终答案 + 工具轨迹 + 汇总用量 + 模型名。
 */
public record FrameworkAgentRunResponse(
        String taskId,
        String answer,
        List<FrameworkAgentStepDto> steps,
        int totalSteps,
        String model,
        FrameworkUsageDto usage
) {

    /**
     * 从用例出参转换。
     */
    public static FrameworkAgentRunResponse from(FrameworkAgentResult result) {
        List<FrameworkAgentStepDto> stepDtos = result.steps().stream()
                .map(FrameworkAgentRunResponse::toStep)
                .toList();
        return new FrameworkAgentRunResponse(
                result.taskId(),
                result.answer(),
                stepDtos,
                result.totalSteps(),
                result.model(),
                new FrameworkUsageDto(
                        result.usage().promptTokens(),
                        result.usage().completionTokens(),
                        result.usage().totalTokens()));
    }

    private static FrameworkAgentStepDto toStep(FrameworkAgentStep step) {
        return new FrameworkAgentStepDto(step.stepNo(), step.toolName(), step.arguments(), step.observation());
    }
}
