package com.aicode.demo.controller;

/**
 * 单次调用 Token 用量的对外视图。
 */
public record UsageDto(int promptTokens, int completionTokens, int totalTokens) {
}
