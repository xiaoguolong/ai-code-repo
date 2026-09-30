package com.aicode.framework.platform.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.time.Instant;

/**
 * Agent 执行记录。
 */
public record ExecutionRecord(
        String executionId,
        String agentKey,
        AgentType agentType,
        ExecutionStatus status,
        String inputJson,
        String outputJson,
        String model,
        TokenUsage usage,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
