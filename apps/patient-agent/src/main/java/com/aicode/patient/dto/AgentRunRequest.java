package com.aicode.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 运行入参。任务描述非空且限长，防止滥用。
 */
public record AgentRunRequest(
        @NotBlank(message = "task 不能为空")
        @Size(max = 8000, message = "task 长度不能超过 8000") String task
) {
}
