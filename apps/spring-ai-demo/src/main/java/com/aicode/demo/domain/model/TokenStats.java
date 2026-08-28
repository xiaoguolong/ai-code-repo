package com.aicode.demo.domain.model;

/**
 * 进程内累计 Token 统计，供成本观察。第1周不落库。
 */
public record TokenStats(long requestCount, long promptTokens, long completionTokens, long totalTokens) {
}
