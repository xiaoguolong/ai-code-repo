package com.aicode.framework.platform.infrastructure.config;

import com.aicode.framework.operations.domain.OperationsThresholds;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运营分析配置（Week 24，{@code platform.operations.*}）。
 *
 * <p>阈值与窗口都可由环境变量覆盖；非法值在 {@link #thresholds()} 里夹到默认值，
 * 避免「配置写错 → 告警静默失效」。</p>
 */
@ConfigurationProperties(prefix = "platform.operations")
public class PlatformOperationsProperties {

    /** 单次统计最多读取的执行记录数（超出置 truncated=true）。 */
    private int maxRecords = 5000;
    /** 默认统计窗口天数。 */
    private int defaultWindowDays = 7;
    /** 允许的最大窗口天数。 */
    private int maxWindowDays = 30;
    /** 失败率「高」阈值（0–1）。 */
    private double failureRateHigh = 0.30;
    /** 失败率「中」阈值（0–1）。 */
    private double failureRateMedium = 0.20;
    /** 待审积压条数阈值。 */
    private int approvalBacklog = 20;
    /** token 突增倍数阈值。 */
    private double tokenSpikeRatio = 2.0;
    /** 单 Agent 失败次数下限。 */
    private int agentFailureMin = 3;

    /** @return 读取上限（小于 1 时回落 5000） */
    public int resolvedMaxRecords() {
        return maxRecords > 0 ? maxRecords : 5000;
    }

    /** @return 默认窗口天数（夹在 1–{@link #resolvedMaxWindowDays()}） */
    public int resolvedDefaultWindowDays() {
        int days = defaultWindowDays > 0 ? defaultWindowDays : 7;
        return Math.min(days, resolvedMaxWindowDays());
    }

    /** @return 最大窗口天数（小于 1 时回落 30） */
    public int resolvedMaxWindowDays() {
        return maxWindowDays > 0 ? maxWindowDays : 30;
    }

    /** @return 归一化后的异常阈值 */
    public OperationsThresholds thresholds() {
        return new OperationsThresholds(
                failureRateHigh, failureRateMedium, approvalBacklog, tokenSpikeRatio, agentFailureMin)
                .normalized();
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public int getDefaultWindowDays() {
        return defaultWindowDays;
    }

    public void setDefaultWindowDays(int defaultWindowDays) {
        this.defaultWindowDays = defaultWindowDays;
    }

    public int getMaxWindowDays() {
        return maxWindowDays;
    }

    public void setMaxWindowDays(int maxWindowDays) {
        this.maxWindowDays = maxWindowDays;
    }

    public double getFailureRateHigh() {
        return failureRateHigh;
    }

    public void setFailureRateHigh(double failureRateHigh) {
        this.failureRateHigh = failureRateHigh;
    }

    public double getFailureRateMedium() {
        return failureRateMedium;
    }

    public void setFailureRateMedium(double failureRateMedium) {
        this.failureRateMedium = failureRateMedium;
    }

    public int getApprovalBacklog() {
        return approvalBacklog;
    }

    public void setApprovalBacklog(int approvalBacklog) {
        this.approvalBacklog = approvalBacklog;
    }

    public double getTokenSpikeRatio() {
        return tokenSpikeRatio;
    }

    public void setTokenSpikeRatio(double tokenSpikeRatio) {
        this.tokenSpikeRatio = tokenSpikeRatio;
    }

    public int getAgentFailureMin() {
        return agentFailureMin;
    }

    public void setAgentFailureMin(int agentFailureMin) {
        this.agentFailureMin = agentFailureMin;
    }
}
