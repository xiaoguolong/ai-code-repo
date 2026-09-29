package com.aicode.framework.multiagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 医疗助手多 Agent 启动请求。
 */
public record MedicalAssistantRunRequest(
        @NotBlank(message = "patientId must not be blank") String patientId,
        String task
) {
}
