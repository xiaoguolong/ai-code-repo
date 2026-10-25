package com.aicode.framework.operations.domain;

import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsAnomaly;
import com.aicode.framework.operations.domain.model.OperationsAnomalyCode;
import com.aicode.framework.operations.domain.model.OperationsAnomalyLevel;
import com.aicode.framework.operations.domain.model.OperationsSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 运营异常检测（Week 24）：6 条确定性规则，全部可解释。
 *
 * <p>为什么不用模型判断异常：阈值判断要求「同样的输入永远得到同样的结论」，
 * 且报告里要能写出「实测值 vs 阈值」；模型只负责把结论写成日报正文。</p>
 *
 * <p>输出顺序固定：级别（HIGH → INFO）→ 规则码字典序，最多 {@value #MAX_ANOMALIES} 条。</p>
 */
public class OperationsAnomalyDetector {

    /** 单次日报最多返回的异常条数。 */
    public static final int MAX_ANOMALIES = 20;

    /** 失败率规则要求的最小分母：样本太小时比率没有意义，只会制造噪声。 */
    static final long MIN_DENOMINATOR = 5L;

    /**
     * 检测异常。
     *
     * @param summary         本窗口汇总
     * @param agents          按 Agent 分组
     * @param previousTokens  上一等长窗口的 token 合计
     * @param pendingApprovals 当前可见范围内的待审审批数
     * @param thresholds      阈值
     * @return 已排序的异常列表，无异常时为空列表
     */
    public List<OperationsAnomaly> detect(
            OperationsSummary summary,
            List<AgentRunSummary> agents,
            long previousTokens,
            long pendingApprovals,
            OperationsThresholds thresholds
    ) {
        OperationsThresholds limits = thresholds == null
                ? OperationsThresholds.defaults() : thresholds.normalized();
        List<OperationsAnomaly> anomalies = new ArrayList<>();

        if (summary.totalRuns() == 0) {
            anomalies.add(new OperationsAnomaly(
                    OperationsAnomalyCode.NO_TRAFFIC, OperationsAnomalyLevel.INFO,
                    "窗口内没有执行记录", 0, 0));
        }

        detectFailureRate(summary, limits, anomalies);
        detectBacklog(pendingApprovals, limits, anomalies);
        detectTokenSpike(summary, previousTokens, limits, anomalies);
        detectAgentConcentration(agents, limits, anomalies);

        anomalies.sort(Comparator
                .comparingInt((OperationsAnomaly anomaly) -> anomaly.level().rank())
                .thenComparing(anomaly -> anomaly.code().name()));
        return anomalies.size() <= MAX_ANOMALIES
                ? List.copyOf(anomalies)
                : List.copyOf(anomalies.subList(0, MAX_ANOMALIES));
    }

    private void detectFailureRate(
            OperationsSummary summary,
            OperationsThresholds limits,
            List<OperationsAnomaly> anomalies
    ) {
        long denominator = summary.denominator();
        if (denominator < MIN_DENOMINATOR) {
            return;
        }
        double failureRate = summary.failureRate();
        String detail = String.format(Locale.ROOT, "失败率 %.1f%%（%d/%d）",
                failureRate * 100, summary.failed(), denominator);
        if (failureRate >= limits.failureRateHigh()) {
            anomalies.add(new OperationsAnomaly(
                    OperationsAnomalyCode.FAILURE_RATE_HIGH, OperationsAnomalyLevel.HIGH,
                    detail + String.format(Locale.ROOT, "，超过高阈值 %.0f%%",
                            limits.failureRateHigh() * 100),
                    failureRate, limits.failureRateHigh()));
        } else if (failureRate >= limits.failureRateMedium()) {
            anomalies.add(new OperationsAnomaly(
                    OperationsAnomalyCode.FAILURE_RATE_ELEVATED, OperationsAnomalyLevel.MEDIUM,
                    detail + String.format(Locale.ROOT, "，超过中阈值 %.0f%%",
                            limits.failureRateMedium() * 100),
                    failureRate, limits.failureRateMedium()));
        }
    }

    private void detectBacklog(
            long pendingApprovals,
            OperationsThresholds limits,
            List<OperationsAnomaly> anomalies
    ) {
        if (pendingApprovals > limits.approvalBacklog()) {
            anomalies.add(new OperationsAnomaly(
                    OperationsAnomalyCode.APPROVAL_BACKLOG, OperationsAnomalyLevel.HIGH,
                    String.format(Locale.ROOT, "待审审批 %d 条，超过阈值 %d 条",
                            pendingApprovals, limits.approvalBacklog()),
                    pendingApprovals, limits.approvalBacklog()));
        }
    }

    private void detectTokenSpike(
            OperationsSummary summary,
            long previousTokens,
            OperationsThresholds limits,
            List<OperationsAnomaly> anomalies
    ) {
        long currentTokens = summary.totalTokens();
        if (previousTokens <= 0 || currentTokens <= 0) {
            return;
        }
        double ratio = (double) currentTokens / previousTokens;
        if (ratio >= limits.tokenSpikeRatio()) {
            anomalies.add(new OperationsAnomaly(
                    OperationsAnomalyCode.TOKEN_SPIKE, OperationsAnomalyLevel.MEDIUM,
                    String.format(Locale.ROOT, "本窗口 token %d，是上一窗口 %d 的 %.1f 倍",
                            currentTokens, previousTokens, ratio),
                    ratio, limits.tokenSpikeRatio()));
        }
    }

    private void detectAgentConcentration(
            List<AgentRunSummary> agents,
            OperationsThresholds limits,
            List<OperationsAnomaly> anomalies
    ) {
        if (agents == null) {
            return;
        }
        for (AgentRunSummary agent : agents) {
            if (agent.failures() >= limits.agentFailureMin() && agent.failureRate() >= 0.5) {
                anomalies.add(new OperationsAnomaly(
                        OperationsAnomalyCode.AGENT_FAILURE_CONCENTRATION, OperationsAnomalyLevel.MEDIUM,
                        String.format(Locale.ROOT, "Agent %s 失败 %d 次 / 执行 %d 次（%.0f%%）",
                                agent.agentKey(), agent.failures(), agent.runs(), agent.failureRate() * 100),
                        agent.failureRate(), 0.5));
            }
        }
    }
}
