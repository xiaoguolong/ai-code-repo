package com.aicode.patient.dto;

import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentStep;

import java.util.List;

/**
 * Agent 运行成功体：最终答案 + 完整工具调用轨迹 + 汇总用量。
 */
public record AgentRunResponse(
        String taskId,
        String answer,
        List<AgentStepDto> steps,
        int totalSteps,
        String model,
        AgentUsageDto usage
) {

    /**
     * 从用例出参转换。
     */
    public static AgentRunResponse from(AgentResult result) {
        List<AgentStepDto> stepDtos = result.steps().stream()
                .map(AgentRunResponse::toStep)
                .toList();
        return new AgentRunResponse(
                result.taskId(),
                result.answer(),
                stepDtos,
                result.totalSteps(),
                result.model(),
                new AgentUsageDto(
                        result.totalUsage().promptTokens(),
                        result.totalUsage().completionTokens(),
                        result.totalUsage().totalTokens()
                )
        );
    }

    private static AgentStepDto toStep(AgentStep step) {
        return new AgentStepDto(step.stepNo(), step.toolName(), step.arguments(), step.observation());
    }
}
