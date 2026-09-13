package com.aicode.patient.dto;

/**
 * 汇总 Token 用量响应体。
 */
public record AgentUsageDto(int promptTokens, int completionTokens, int totalTokens) {
}
