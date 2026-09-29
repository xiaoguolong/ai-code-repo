package com.aicode.framework.multiagent.dto;

import com.aicode.framework.dto.FrameworkUsageDto;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.workflow.dto.HealthMetricsDto;
import com.aicode.framework.workflow.dto.PatientProfileDto;

import java.util.List;

/**
 * 医疗助手多 Agent 成功响应体。
 */
public record MedicalAssistantRunResponse(
        String runId,
        String patientId,
        String task,
        PatientProfileDto patient,
        HealthMetricsDto metrics,
        String riskLevel,
        String riskLabel,
        String justification,
        String report,
        String followUpPlan,
        String model,
        FrameworkUsageDto usage,
        List<MedicalAssistantStepDto> steps
) {

    /** 从用例出参转换。 */
    public static MedicalAssistantRunResponse from(MedicalAssistantResult result) {
        return new MedicalAssistantRunResponse(
                result.runId(),
                result.patientId(),
                result.task(),
                result.patient() == null ? null : PatientProfileDto.from(result.patient()),
                result.metrics() == null ? null : HealthMetricsDto.from(result.metrics()),
                result.riskLevel().name(),
                result.riskLevel().label(),
                result.justification(),
                result.report(),
                result.followUpPlan(),
                result.model(),
                new FrameworkUsageDto(
                        result.usage().promptTokens(),
                        result.usage().completionTokens(),
                        result.usage().totalTokens()),
                result.steps().stream().map(MedicalAssistantRunResponse::toStepDto).toList());
    }

    private static MedicalAssistantStepDto toStepDto(MedicalAssistantStep step) {
        return new MedicalAssistantStepDto(step.stepNo(), step.agentName(), step.summary());
    }
}
