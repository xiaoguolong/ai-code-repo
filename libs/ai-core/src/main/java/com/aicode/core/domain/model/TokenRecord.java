package com.aicode.core.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 单次模型调用的 Token 记录。归属用户与会话，便于按用户统计成本。
 */
public record TokenRecord(
        Long userId,
        String sessionId,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        Instant createdAt
) {

    public TokenRecord {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
