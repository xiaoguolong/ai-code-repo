package com.aicode.framework.workflow.dto;

import com.aicode.framework.dto.FrameworkUsageDto;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;

/**
 * 患者风险分析 Workflow 成功响应体。
 */
public record PatientRiskRunResponse(
        String workflowId,
        String patientId,
        PatientProfileDto patient,
        HealthMetricsDto metrics,
        String riskLevel,
        String riskLabel,
        String justification,
        boolean escalated,
        String report,
        String model,
        FrameworkUsageDto usage
) {

    /**
     * 从用例出参转换。
     */
    public static PatientRiskRunResponse from(PatientRiskWorkflowResult result) {
        return new PatientRiskRunResponse(
                result.workflowId(),
                result.patientId(),
                result.patient() == null ? null : PatientProfileDto.from(result.patient()),
                result.metrics() == null ? null : HealthMetricsDto.from(result.metrics()),
                result.riskLevel().name(),
                result.riskLevel().label(),
                result.justification(),
                result.escalated(),
                result.report(),
                result.model(),
                new FrameworkUsageDto(
                        result.usage().promptTokens(),
                        result.usage().completionTokens(),
                        result.usage().totalTokens()));
    }
}
