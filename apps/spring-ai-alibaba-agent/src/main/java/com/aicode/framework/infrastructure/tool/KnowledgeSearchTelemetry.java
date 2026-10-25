package com.aicode.framework.infrastructure.tool;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.ObservabilityAttributes;

/**
 * 知识库检索专项指标（Week 27，包内协作类）。
 *
 * <p>工具只负责参数解析与绑定校验；指标口径（Timer / Counter 的名字、outcome 取值）
 * 集中在这里，避免工具类因为埋点膨胀。</p>
 */
final class KnowledgeSearchTelemetry {

    private final AgentObservabilityPort observability;

    KnowledgeSearchTelemetry(AgentObservabilityPort observability) {
        this.observability = observability;
    }

    /**
     * 开始计时。
     *
     * @return 计时句柄（记录起始纳秒）
     */
    Call start() {
        return new Call(System.nanoTime());
    }

    /**
     * 检索成功：累加命中数 + 记录成功耗时。
     *
     * @param call     计时句柄
     * @param hitCount 本次命中切片数
     */
    void success(Call call, int hitCount) {
        observability.recordCounter(MonitoringMetricNames.KNOWLEDGE_HIT_COUNT, hitCount);
        record(call, ObservabilityAttributes.OUTCOME_SUCCESS);
    }

    /**
     * 检索失败：只记录失败耗时（不累加命中数）。
     *
     * @param call 计时句柄
     */
    void failure(Call call) {
        record(call, ObservabilityAttributes.OUTCOME_FAILURE);
    }

    private void record(Call call, String outcome) {
        long durationMs = Math.max(0L, (System.nanoTime() - call.startNanos()) / 1_000_000L);
        observability.recordDuration(MonitoringMetricNames.KNOWLEDGE_SEARCH_DURATION, durationMs,
                ObservabilityAttributes.KNOWLEDGE_OUTCOME, outcome);
    }

    /**
     * 一次检索的计时句柄。
     *
     * @param startNanos 起始纳秒（{@link System#nanoTime()}）
     */
    record Call(long startNanos) {
    }
}
