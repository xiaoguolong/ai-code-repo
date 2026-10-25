package com.aicode.framework.operations.domain;

import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsAnomaly;
import com.aicode.framework.operations.domain.model.OperationsAnomalyCode;
import com.aicode.framework.operations.domain.model.OperationsAnomalyLevel;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运营异常规则（Week 24）：6 条规则的命中、阈值边界与不误报。
 */
class OperationsAnomalyDetectorTest {

    private final OperationsAnomalyDetector detector = new OperationsAnomalyDetector();

    @Test
    void highFailureRateIsReportedAsHigh() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(4, 6, 0, 100), List.of(), 0, 0, OperationsThresholds.defaults());

        assertThat(anomalies).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.code()).isEqualTo(OperationsAnomalyCode.FAILURE_RATE_HIGH);
            assertThat(anomaly.level()).isEqualTo(OperationsAnomalyLevel.HIGH);
            assertThat(anomaly.value()).isEqualTo(0.4);
            assertThat(anomaly.message()).contains("40.0%").contains("4/10");
        });
    }

    @Test
    void failureRateBetweenThresholdsIsOnlyElevated() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(2, 8, 0, 100), List.of(), 0, 0, OperationsThresholds.defaults());

        assertThat(anomalies).singleElement()
                .extracting(OperationsAnomaly::code)
                .isEqualTo(OperationsAnomalyCode.FAILURE_RATE_ELEVATED);
    }

    @Test
    void differentRatesBelowMediumThresholdReportNothing() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(1, 9, 0, 100), List.of(), 0, 0, OperationsThresholds.defaults());

        assertThat(anomalies).isEmpty();
    }

    @Test
    void tinySampleDoesNotTriggerFailureRateRules() {
        // 2/2 失败 = 100%，但分母 < 5：样本太小，不制造噪声。
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(2, 0, 0, 10), List.of(), 0, 0, OperationsThresholds.defaults());

        assertThat(anomalies).isEmpty();
    }

    @Test
    void approvalBacklogAboveThresholdIsHigh() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(0, 10, 5, 100), List.of(), 0, 21, OperationsThresholds.defaults());

        assertThat(anomalies).anySatisfy(anomaly -> {
            assertThat(anomaly.code()).isEqualTo(OperationsAnomalyCode.APPROVAL_BACKLOG);
            assertThat(anomaly.value()).isEqualTo(21);
            assertThat(anomaly.threshold()).isEqualTo(20);
        });
    }

    @Test
    void approvalBacklogAtThresholdDoesNotTrigger() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(0, 10, 5, 100), List.of(), 0, 20, OperationsThresholds.defaults());

        assertThat(anomalies).noneMatch(anomaly -> anomaly.code() == OperationsAnomalyCode.APPROVAL_BACKLOG);
    }

    @Test
    void tokenSpikeNeedsAUsablePreviousWindow() {
        List<OperationsAnomaly> noPrevious = detector.detect(
                summary(0, 10, 0, 30_000), List.of(), 0, 0, OperationsThresholds.defaults());
        assertThat(noPrevious).noneMatch(anomaly -> anomaly.code() == OperationsAnomalyCode.TOKEN_SPIKE);

        List<OperationsAnomaly> spike = detector.detect(
                summary(0, 10, 0, 30_000), List.of(), 10_000, 0, OperationsThresholds.defaults());
        assertThat(spike).anySatisfy(anomaly -> {
            assertThat(anomaly.code()).isEqualTo(OperationsAnomalyCode.TOKEN_SPIKE);
            assertThat(anomaly.value()).isEqualTo(3.0);
        });
    }

    @Test
    void agentFailureConcentrationRequiresCountAndMajority() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(3, 20, 0, 100),
                List.of(new AgentRunSummary("patient-analysis", 5, 3, 1000)), 0, 0,
                OperationsThresholds.defaults());

        assertThat(anomalies).anySatisfy(anomaly -> {
            assertThat(anomaly.code()).isEqualTo(OperationsAnomalyCode.AGENT_FAILURE_CONCENTRATION);
            assertThat(anomaly.message()).contains("patient-analysis");
        });
    }

    @Test
    void agentWithFailuresBelowMinCountIsIgnored() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(0, 20, 0, 100),
                List.of(new AgentRunSummary("patient-analysis", 1, 1, 100)), 0, 0,
                OperationsThresholds.defaults());

        assertThat(anomalies).isEmpty();
    }

    @Test
    void emptyWindowIsReportedAsInfo() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(0, 0, 0, 0), List.of(), 0, 0, OperationsThresholds.defaults());

        assertThat(anomalies).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.code()).isEqualTo(OperationsAnomalyCode.NO_TRAFFIC);
            assertThat(anomaly.level()).isEqualTo(OperationsAnomalyLevel.INFO);
        });
    }

    @Test
    void anomaliesAreSortedByLevelRankThenCode() {
        List<OperationsAnomaly> anomalies = detector.detect(
                summary(4, 6, 0, 30_000), List.of(), 1_000, 25, OperationsThresholds.defaults());

        assertThat(anomalies).extracting(OperationsAnomaly::level)
                .isSortedAccordingTo((left, right) -> Integer.compare(left.rank(), right.rank()));
        assertThat(anomalies.get(0).level()).isEqualTo(OperationsAnomalyLevel.HIGH);
    }

    @Test
    void invalidThresholdsFallBackToDefaults() {
        OperationsThresholds broken = new OperationsThresholds(0, -1, 0, 0, 0);

        assertThat(broken.normalized()).isEqualTo(OperationsThresholds.defaults());
    }

    private OperationsSummary summary(long failed, long completed, long pending, long totalTokens) {
        return new OperationsSummary(
                failed + completed + pending, completed, failed, pending, 0, 0,
                totalTokens, 0, 0.0, true, false);
    }
}
