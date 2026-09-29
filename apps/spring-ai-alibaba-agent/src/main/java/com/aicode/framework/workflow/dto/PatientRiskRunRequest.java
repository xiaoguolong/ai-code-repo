package com.aicode.framework.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 患者风险分析 Workflow 入参。patientId 非空且限长，防止滥用。
 */
public record PatientRiskRunRequest(
        @NotBlank(message = "patientId 不能为空")
        @Size(max = 100, message = "patientId 长度不能超过 100") String patientId
) {
}
