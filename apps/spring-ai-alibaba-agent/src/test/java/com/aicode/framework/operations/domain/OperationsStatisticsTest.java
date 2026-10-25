package com.aicode.framework.operations.domain;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运营统计口径（Week 24）：状态计数、成功率分母、token、成本与按 Agent 分组。
 */
class OperationsStatisticsTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final ModelPriceCatalog PRICES = new ModelPriceCatalog(
            Map.of("deepseek-chat", new ModelPrice(1.0, 2.0)));

    private final OperationsStatistics statistics = new OperationsStatistics();

    @Test
    void countsStatusesAndExcludesPendingFromTheRateDenominator() {
        List<ExecutionRecord> records = List.of(
                record("e1", ExecutionStatus.COMPLETED, 100, 50, "deepseek-chat"),
                record("e2", ExecutionStatus.COMPLETED, 10, 10, "deepseek-chat"),
                record("e3", ExecutionStatus.FAILED, 0, 0, "deepseek-chat"),
                record("e4", ExecutionStatus.PENDING_APPROVAL, 0, 0, "deepseek-chat"),
                record("e5", ExecutionStatus.REJECTED, 0, 0, "deepseek-chat"),
                record("e6", ExecutionStatus.RUNNING, 0, 0, "deepseek-chat"));

        OperationsSummary summary = statistics.summarize(records, PRICES, false);

        assertThat(summary.totalRuns()).isEqualTo(6);
        assertThat(summary.completed()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.pendingApproval()).isEqualTo(1);
        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(summary.running()).isEqualTo(1);
        assertThat(summary.denominator()).isEqualTo(3);
        assertThat(summary.successRate()).isEqualTo(0.6667);
        assertThat(summary.failureRate()).isEqualTo(0.3333);
    }

    @Test
    void ratesAreZeroWhenThereIsNoCompletedOrFailedRun() {
        OperationsSummary summary = statistics.summarize(
                List.of(record("e1", ExecutionStatus.PENDING_APPROVAL, 0, 0, "deepseek-chat")),
                PRICES, false);

        assertThat(summary.denominator()).isZero();
        assertThat(summary.successRate()).isZero();
        assertThat(summary.failureRate()).isZero();
    }

    @Test
    void sumsTokensAndConvertsCostPerMillionTokens() {
        List<ExecutionRecord> records = List.of(
                record("e1", ExecutionStatus.COMPLETED, 1_000_000, 500_000, "deepseek-chat"),
                record("e2", ExecutionStatus.COMPLETED, 1_000_000, 0, "deepseek-chat"));

        OperationsSummary summary = statistics.summarize(records, PRICES, false);

        assertThat(summary.promptTokens()).isEqualTo(2_000_000);
        assertThat(summary.completionTokens()).isEqualTo(500_000);
        assertThat(summary.totalTokens()).isEqualTo(2_500_000);
        // 1.0 USD/M * 2M prompt + 2.0 USD/M * 0.5M completion = 2 + 1
        assertThat(summary.totalCostUsd()).isEqualTo(3.0);
        assertThat(summary.costConfigured()).isTrue();
    }

    @Test
    void unknownModelPriceMarksPartialCostInsteadOfInventingZeroCost() {
        List<ExecutionRecord> records = List.of(
                record("e1", ExecutionStatus.COMPLETED, 1_000_000, 0, "deepseek-chat"),
                record("e2", ExecutionStatus.COMPLETED, 1_000_000, 0, "unknown-model"));

        OperationsSummary summary = statistics.summarize(records, PRICES, false);

        assertThat(summary.totalCostUsd()).isEqualTo(1.0);
        assertThat(summary.costConfigured()).isFalse();
    }

    @Test
    void marksTruncationFlagAsGiven() {
        OperationsSummary summary = statistics.summarize(List.of(), PRICES, true);

        assertThat(summary.truncated()).isTrue();
        assertThat(summary.totalRuns()).isZero();
        assertThat(summary.costConfigured()).isTrue();
    }

    @Test
    void groupsByAgentOrderedByRunsDescendingThenKey() {
        List<ExecutionRecord> records = List.of(
                agentRecord("e1", "patient-analysis", ExecutionStatus.COMPLETED, 100, 100),
                agentRecord("e2", "patient-analysis", ExecutionStatus.FAILED, 0, 0),
                agentRecord("e3", "operations-analysis", ExecutionStatus.COMPLETED, 10, 10),
                agentRecord("e4", "followup", ExecutionStatus.COMPLETED, 1, 1),
                agentRecord("e5", "followup", ExecutionStatus.COMPLETED, 1, 1));

        List<AgentRunSummary> agents = statistics.byAgent(records);

        assertThat(agents).extracting(AgentRunSummary::agentKey)
                .containsExactly("followup", "patient-analysis", "operations-analysis");
        assertThat(agents.get(0).runs()).isEqualTo(2);
        AgentRunSummary patient = agents.stream()
                .filter(agent -> "patient-analysis".equals(agent.agentKey()))
                .findFirst()
                .orElseThrow();
        assertThat(patient.failures()).isEqualTo(1);
        assertThat(patient.tokens()).isEqualTo(200);
        assertThat(patient.failureRate()).isEqualTo(0.5);
    }

    @Test
    void countsTokensForPreviousWindowComparison() {
        assertThat(statistics.tokens(List.of(
                record("e1", ExecutionStatus.COMPLETED, 100, 50, "deepseek-chat"),
                record("e2", ExecutionStatus.FAILED, 10, 5, "deepseek-chat"))))
                .isEqualTo(165);
        assertThat(statistics.tokens(null)).isZero();
    }

    private ExecutionRecord agentRecord(
            String id, String agentKey, ExecutionStatus status, int prompt, int completion) {
        return new ExecutionRecord(
                id, 2L, agentKey,
                "operations-analysis".equals(agentKey) ? AgentType.OPERATIONS_ANALYSIS : AgentType.PATIENT_ANALYSIS,
                status, "{}", "{}", "deepseek-chat",
                new TokenUsage(prompt, completion, prompt + completion), "", NOW, NOW);
    }

    private ExecutionRecord record(
            String id, ExecutionStatus status, int prompt, int completion, String model) {
        return new ExecutionRecord(
                id, 2L, "patient-analysis", AgentType.PATIENT_ANALYSIS, status,
                "{}", "{}", model, new TokenUsage(prompt, completion, prompt + completion), "", NOW, NOW);
    }
}
