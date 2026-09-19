package com.aicode.core.domain.model;

/**
 * 累计 Token 统计，供成本观察。
 */
public record TokenStats(long requestCount, long promptTokens, long completionTokens, long totalTokens) {

    /**
     * 合并另一份统计。
     */
    public TokenStats plus(TokenStats other) {
        return new TokenStats(
                this.requestCount + other.requestCount,
                this.promptTokens + other.promptTokens,
                this.completionTokens + other.completionTokens,
                this.totalTokens + other.totalTokens
        );
    }
}
