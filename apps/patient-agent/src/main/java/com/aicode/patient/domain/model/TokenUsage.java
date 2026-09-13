package com.aicode.patient.domain.model;

/**
 * 单次模型调用的 Token 用量。厂商未返回 usage 时用 {@link #unknown()}。
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    /**
     * 用量未知时的占位，全部记 0，避免空指针。
     */
    public static TokenUsage unknown() {
        return new TokenUsage(0, 0, 0);
    }

    /**
     * 累加两次用量，供 Agent 多轮迭代汇总。
     */
    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens
        );
    }
}
