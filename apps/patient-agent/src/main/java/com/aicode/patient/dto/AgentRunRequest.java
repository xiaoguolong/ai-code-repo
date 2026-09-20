package com.aicode.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 运行入参。sessionId 可选（为空时服务端生成），任务描述非空且限长，防止滥用。
 */
public record AgentRunRequest(
        @Size(max = 100, message = "sessionId 长度不能超过 100") String sessionId,
        @NotBlank(message = "task 不能为空")
        @Size(max = 8000, message = "task 长度不能超过 8000") String task
) {
}
