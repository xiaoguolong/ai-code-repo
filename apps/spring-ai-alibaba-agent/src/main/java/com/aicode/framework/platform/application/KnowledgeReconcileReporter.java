package com.aicode.framework.platform.application;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对账指标上报（Week 27，包内协作类）。
 *
 * <p>把指标名/标签口径从对账用例里拆出来：用例只关心差异计算，
 * 指标契约集中在 {@link MonitoringMetricNames} 与这里。</p>
 */
final class KnowledgeReconcileReporter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeReconcileReporter.class);

    private static final String RESULT_UPDATED = "updated";
    private static final String RESULT_MISSING = "missing";
    private static final String RESULT_UNCHANGED = "unchanged";
    private static final String RESULT_FAILED = "failed";

    private final KnowledgeBindingPort bindingPort;
    private final AgentObservabilityPort observability;

    KnowledgeReconcileReporter(KnowledgeBindingPort bindingPort, AgentObservabilityPort observability) {
        this.bindingPort = bindingPort;
        this.observability = observability;
    }

    /**
     * 记录本轮对账的四类结果计数（0 也记录，保证序列稳定）。
     *
     * @param updated   更新条数
     * @param missing   标记 MISSING 条数
     * @param unchanged 未变化条数
     * @param failed    失败条数
     */
    void reportCounters(int updated, int missing, int unchanged, int failed) {
        counter(updated, RESULT_UPDATED);
        counter(missing, RESULT_MISSING);
        counter(unchanged, RESULT_UNCHANGED);
        counter(failed, RESULT_FAILED);
    }

    /**
     * 刷新「企业侧已删除」Gauge。
     */
    void reportMissingGauge() {
        try {
            long missing = bindingPort.countBySyncStatus(KnowledgeBindingSyncStatus.MISSING);
            observability.recordGauge(MonitoringMetricNames.KNOWLEDGE_BINDING_MISSING_COUNT, missing);
        } catch (RuntimeException ex) {
            log.debug("[knowledge] missing binding gauge failed: {}", ex.toString());
        }
    }

    private void counter(int amount, String result) {
        observability.recordCounter(MonitoringMetricNames.KNOWLEDGE_RECONCILE_COUNT, amount,
                ObservabilityAttributes.KNOWLEDGE_RESULT, result);
    }
}
