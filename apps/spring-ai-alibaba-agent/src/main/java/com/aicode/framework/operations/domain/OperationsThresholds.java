package com.aicode.framework.operations.domain;

/**
 * 运营异常阈值（Week 24）。
 *
 * <p>只放「判断规则」需要的数字，由 {@code PlatformOperationsProperties} 从配置映射而来；
 * 领域层不依赖 Spring 配置类型。</p>
 *
 * @param failureRateHigh   失败率「高」阈值（0–1）
 * @param failureRateMedium 失败率「中」阈值（0–1）
 * @param approvalBacklog   待审积压条数阈值
 * @param tokenSpikeRatio   本窗口 / 上一窗口的 token 倍数阈值
 * @param agentFailureMin   单 Agent 失败次数下限（同时要求失败占比 ≥ 50%）
 */
public record OperationsThresholds(
        double failureRateHigh,
        double failureRateMedium,
        int approvalBacklog,
        double tokenSpikeRatio,
        int agentFailureMin
) {

    /**
     * @return 与 Spec 一致的默认阈值
     */
    public static OperationsThresholds defaults() {
        return new OperationsThresholds(0.30, 0.20, 20, 2.0, 3);
    }

    /**
     * 防护：配置写成负数/零时会静默失去告警能力，这里统一夹到默认值。
     *
     * @return 合法阈值
     */
    public OperationsThresholds normalized() {
        OperationsThresholds defaults = defaults();
        return new OperationsThresholds(
                failureRateHigh > 0 && failureRateHigh <= 1 ? failureRateHigh : defaults.failureRateHigh,
                failureRateMedium > 0 && failureRateMedium <= 1 ? failureRateMedium : defaults.failureRateMedium,
                approvalBacklog > 0 ? approvalBacklog : defaults.approvalBacklog,
                tokenSpikeRatio > 1 ? tokenSpikeRatio : defaults.tokenSpikeRatio,
                agentFailureMin > 0 ? agentFailureMin : defaults.agentFailureMin);
    }
}
