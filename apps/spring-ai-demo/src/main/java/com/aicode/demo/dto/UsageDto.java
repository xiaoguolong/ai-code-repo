package com.aicode.demo.dto;

/**
 * 单次调用 Token 用量的对外视图。
 */
public record UsageDto(int promptTokens, int completionTokens, int totalTokens) {
}
