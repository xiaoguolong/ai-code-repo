package com.aicode.framework.operations.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 运营统计汇总（Week 24）。
 *
 * <p><b>成功/失败率的分母只取 {@code completed + failed}</b>：待审（{@code PENDING_APPROVAL}）、
 * 被拒（{@code REJECTED}）与运行中（{@code RUNNING}）都不是「失败」。
 * 这个口径与 Grafana 失败率面板（TD-01）必须一致，否则面板与日报会各说一套。</p>
 *
 * @param totalRuns        窗口内执行总数
 * @param completed        成功数
 * @param failed           失败数
 * @param pendingApproval  待审数
 * @param rejected         审批拒绝数
 * @param running          运行中数
 * @param promptTokens     提示 token 合计
 * @param completionTokens 补全 token 合计
 * @param totalCostUsd     估算成本（USD，只统计价目表里配置了单价的模型）
 * @param costConfigured   是否所有产生 token 的模型都配了单价
 * @param truncated        是否因读取上限被截断
 */
public record OperationsSummary(
        long totalRuns,
        long completed,
        long failed,
        long pendingApproval,
        long rejected,
        long running,
        long promptTokens,
        long completionTokens,
        double totalCostUsd,
        boolean costConfigured,
        boolean truncated
) {

    /** 成功率/失败率的分母。 */
    public long denominator() {
        return completed + failed;
    }

    /** @return 提示 + 补全 token 合计 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    /** @return 成功率（4 位小数）；分母为 0 时返回 0 */
    public double successRate() {
        return rate(completed);
    }

    /** @return 失败率（4 位小数）；分母为 0 时返回 0 */
    public double failureRate() {
        return rate(failed);
    }

    private double rate(long numerator) {
        long denominator = denominator();
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
