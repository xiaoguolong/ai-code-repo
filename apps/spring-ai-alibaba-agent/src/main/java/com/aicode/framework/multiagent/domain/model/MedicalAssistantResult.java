package com.aicode.framework.multiagent.domain.model;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;

import java.util.List;

/**
 * 医疗助手多 Agent 运行结果。
 */
public record MedicalAssistantResult(
        String runId,
        String patientId,
        String task,
        PatientProfile patient,
        HealthMetrics metrics,
        RiskLevel riskLevel,
        String justification,
        String report,
        String followUpPlan,
        List<MedicalAssistantStep> steps,
        TokenUsage usage,
        String model
) {
}
