package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * 执行记录工厂（Week 28，包内协作类）。
 *
 * <p>只负责按状态构造 {@link ExecutionRecord} 与 JSON 序列化，不写库、不记指标；
 * 状态迁移仍由 {@link PlatformExecutionUseCase} 编排。</p>
 */
final class PlatformExecutionRecorder {

    private final ObjectMapper objectMapper;

    PlatformExecutionRecorder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构造 RUNNING 记录。
     *
     * @param executionId 执行 ID
     * @param userId      用户 ID
     * @param agent       Agent 定义
     * @param inputJson   入参 JSON
     * @param tenantKey   租户
     * @param startedAt   开始时间
     * @return RUNNING 记录
     */
    ExecutionRecord running(
            String executionId,
            long userId,
            PlatformAgentDefinition agent,
            String inputJson,
            String tenantKey,
            Instant startedAt
    ) {
        return new ExecutionRecord(
                executionId, userId, agent.agentKey(), agent.agentType(), ExecutionStatus.RUNNING,
                inputJson, "", "", TokenUsage.unknown(), "", startedAt, null, tenantKey);
    }

    /**
     * 构造 COMPLETED 记录。
     *
     * @param running    运行中记录
     * @param outputJson 输出 JSON
     * @param model      模型名
     * @param usage      token 用量
     * @param finishedAt 完成时间
     * @return COMPLETED 记录
     */
    ExecutionRecord completed(
            ExecutionRecord running,
            String outputJson,
            String model,
            TokenUsage usage,
            Instant finishedAt
    ) {
        return new ExecutionRecord(
                running.executionId(), running.userId(), running.agentKey(), running.agentType(),
                ExecutionStatus.COMPLETED, running.inputJson(), outputJson, model,
                usage == null ? TokenUsage.unknown() : usage, "",
                running.startedAt(), finishedAt, running.tenantKey());
    }

    /**
     * 构造 FAILED 记录。
     *
     * @param running    运行中记录
     * @param error      错误摘要
     * @param finishedAt 失败时间
     * @return FAILED 记录
     */
    ExecutionRecord failed(ExecutionRecord running, String error, Instant finishedAt) {
        return new ExecutionRecord(
                running.executionId(), running.userId(), running.agentKey(), running.agentType(),
                ExecutionStatus.FAILED, running.inputJson(), "", "", TokenUsage.unknown(),
                error == null ? "" : error, running.startedAt(), finishedAt, running.tenantKey());
    }

    /**
     * 构造 PENDING_APPROVAL 记录。
     *
     * @param running    运行中记录
     * @param outputJson 待审输出 JSON（含 approvalId）
     * @param model      模型名
     * @param usage      token 用量
     * @return 待审记录
     */
    ExecutionRecord pending(
            ExecutionRecord running,
            String outputJson,
            String model,
            TokenUsage usage
    ) {
        return new ExecutionRecord(
                running.executionId(), running.userId(), running.agentKey(), running.agentType(),
                ExecutionStatus.PENDING_APPROVAL, running.inputJson(), outputJson, model,
                usage == null ? TokenUsage.unknown() : usage, "",
                running.startedAt(), null, running.tenantKey());
    }

    /**
     * JSON 序列化。
     *
     * @param value 任意值
     * @return JSON 文本
     */
    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize json", ex);
        }
    }
}
