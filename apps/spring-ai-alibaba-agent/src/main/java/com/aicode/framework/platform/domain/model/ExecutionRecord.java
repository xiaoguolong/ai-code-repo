package com.aicode.framework.platform.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.time.Instant;

/**
 * Agent 执行记录。
 *
 * @param executionId 执行 ID
 * @param userId      发起用户
 * @param agentKey    Agent 标识
 * @param agentType   Agent 类型
 * @param status      终态/进行中
 * @param inputJson   入参 JSON
 * @param outputJson  输出 JSON
 * @param model       模型名
 * @param usage       token 用量
 * @param errorMessage 失败原因（成功为空串）
 * @param startedAt   开始时间
 * @param finishedAt  结束时间；未终态为 null
 * @param tenantKey   归属租户（Week 23 B2；旧构造器回落 default）
 */
public record ExecutionRecord(
        String executionId,
        long userId,
        String agentKey,
        AgentType agentType,
        ExecutionStatus status,
        String inputJson,
        String outputJson,
        String model,
        TokenUsage usage,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        String tenantKey
) {

    public ExecutionRecord {
        tenantKey = (tenantKey == null || tenantKey.isBlank())
                ? PlatformTenant.DEFAULT_TENANT_KEY : tenantKey.trim();
    }

    /**
     * Week 13–22 兼容构造器：归属 default 租户。
     *
     * @param executionId  执行 ID
     * @param userId       用户
     * @param agentKey     Agent
     * @param agentType    类型
     * @param status       状态
     * @param inputJson    入参
     * @param outputJson   输出
     * @param model        模型
     * @param usage        用量
     * @param errorMessage 错误
     * @param startedAt    开始
     * @param finishedAt   结束
     */
    public ExecutionRecord(
            String executionId,
            long userId,
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
        this(executionId, userId, agentKey, agentType, status, inputJson, outputJson, model,
                usage, errorMessage, startedAt, finishedAt, PlatformTenant.DEFAULT_TENANT_KEY);
    }
}
