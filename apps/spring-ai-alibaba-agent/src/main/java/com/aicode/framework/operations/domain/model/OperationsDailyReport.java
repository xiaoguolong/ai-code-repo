package com.aicode.framework.operations.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.time.Instant;
import java.util.List;

/**
 * 运营日报（Week 24）。
 *
 * @param windowStart 统计窗口起点（含）
 * @param windowEnd   统计窗口终点（不含）
 * @param windowDays  窗口天数
 * @param summary     汇总数字
 * @param agents      按 Agent 分组
 * @param anomalies   规则异常（已排序，最多 20 条）
 * @param report      Markdown 日报正文（模型生成或规则模板）
 * @param degraded    是否降级为规则模板
 * @param usage       日报生成消耗的 token（降级时为 unknown）
 * @param model       模型名
 */
public record OperationsDailyReport(
        Instant windowStart,
        Instant windowEnd,
        int windowDays,
        OperationsSummary summary,
        List<AgentRunSummary> agents,
        List<OperationsAnomaly> anomalies,
        String report,
        boolean degraded,
        TokenUsage usage,
        String model
) {

    public OperationsDailyReport {
        agents = agents == null ? List.of() : List.copyOf(agents);
        anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
        report = report == null ? "" : report;
        usage = usage == null ? TokenUsage.unknown() : usage;
        model = model == null ? "" : model;
    }
}
