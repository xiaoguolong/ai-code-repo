package com.aicode.framework.platform.application;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.observability.domain.SpanScope;
import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;

import java.util.Map;

/**
 * 执行观测（Week 28，包内协作类）：span 属性 + {@code agent.run} 指标 + Langfuse trace 维度。
 *
 * <p>只做埋点，不参与状态机；指标标签只用低基数的 agentKey / execution.status。</p>
 */
final class PlatformExecutionObservability {

    private final AgentObservabilityPort observability;

    PlatformExecutionObservability(AgentObservabilityPort observability) {
        this.observability = observability;
    }

    /**
     * 构造本次执行的 Langfuse trace 维度。
     *
     * <p>会话语义：用入参里的 {@code patientId} 而不是 executionId——同一患者的多次分析归为一个会话；
     * 缺 patientId 时不写会话维度。</p>
     *
     * @param userId 用户 ID
     * @param agent  Agent 定义
     * @param input  Run 入参
     * @return trace 维度
     */
    TraceDimensions traceDimensions(long userId, PlatformAgentDefinition agent, Map<String, Object> input) {
        Object patientId = input == null ? null : input.get("patientId");
        String sessionId = patientId == null ? null : String.valueOf(patientId);
        return TraceDimensions.agentRun(
                userId, agent.agentKey(), String.valueOf(agent.agentType()), sessionId);
    }

    /**
     * 成功终态：span 属性（模型、token）+ 计数/耗时指标（COMPLETED）。
     *
     * @param span         span
     * @param agent        Agent
     * @param output       运行输出
     * @param runStartedAt 开始纳秒
     */
    void recordSuccess(
            SpanScope span,
            PlatformAgentDefinition agent,
            PlatformRunOutput output,
            long runStartedAt
    ) {
        record(span, agent, output, runStartedAt, ExecutionStatus.COMPLETED);
    }

    /**
     * 待审终态：span 属性 + 计数/耗时指标（PENDING_APPROVAL）。
     *
     * @param span         span
     * @param agent        Agent
     * @param output       运行输出
     * @param runStartedAt 开始纳秒
     */
    void recordPending(
            SpanScope span,
            PlatformAgentDefinition agent,
            PlatformRunOutput output,
            long runStartedAt
    ) {
        record(span, agent, output, runStartedAt, ExecutionStatus.PENDING_APPROVAL);
    }

    /**
     * 失败终态：span 标错误 + 计数/耗时指标（FAILED）。
     *
     * @param span         span
     * @param agent        Agent
     * @param ex           异常
     * @param runStartedAt 开始纳秒
     */
    void recordFailure(
            SpanScope span,
            PlatformAgentDefinition agent,
            RuntimeException ex,
            long runStartedAt
    ) {
        span.recordError(ex);
        span.attribute(ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
        span.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(elapsedMs(runStartedAt)));
        observability.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
        observability.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, elapsedMs(runStartedAt),
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, ExecutionStatus.FAILED.name());
    }

    private void record(
            SpanScope span,
            PlatformAgentDefinition agent,
            PlatformRunOutput output,
            long runStartedAt,
            ExecutionStatus status
    ) {
        long durationMs = elapsedMs(runStartedAt);
        span.attribute(ObservabilityAttributes.EXECUTION_STATUS, status.name());
        span.attribute(ObservabilityAttributes.DURATION_MS, String.valueOf(durationMs));
        if (output.model() != null && !output.model().isBlank()) {
            span.attribute(ObservabilityAttributes.GEN_AI_REQUEST_MODEL, output.model());
        }
        if (output.usage() != null) {
            span.attribute(ObservabilityAttributes.GEN_AI_USAGE_TOTAL_TOKENS,
                    String.valueOf(output.usage().totalTokens()));
        }
        observability.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, status.name());
        observability.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, durationMs,
                ObservabilityAttributes.AGENT_KEY, agent.agentKey(),
                ObservabilityAttributes.EXECUTION_STATUS, status.name());
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
