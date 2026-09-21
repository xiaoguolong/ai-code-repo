package com.aicode.framework.dto;

/**
 * 汇总 Token 用量响应体。
 */
public record FrameworkUsageDto(int promptTokens, int completionTokens, int totalTokens) {
}
