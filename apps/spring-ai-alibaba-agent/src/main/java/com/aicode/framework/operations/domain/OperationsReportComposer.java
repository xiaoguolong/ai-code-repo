package com.aicode.framework.operations.domain;

import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsAnomaly;
import com.aicode.framework.operations.domain.model.OperationsSummary;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 运营日报的规则模板（Week 24 降级路径）。
 *
 * <p>模型不可用、返回空内容或 Prompt 缺失时，用本类生成一份**可直接看**的日报：
 * 数字全部来自 {@link OperationsSummary}，异常全部来自规则检测结果，
 * 因此降级日报的可信度不低于模型版本（只是措辞更机械）。</p>
 */
public class OperationsReportComposer {

    /**
     * 组合降级日报。
     *
     * @param windowStart 窗口起点
     * @param windowEnd   窗口终点
     * @param windowDays  窗口天数
     * @param summary     汇总
     * @param agents      按 Agent 分组
     * @param anomalies   异常
     * @return Markdown 正文
     */
    public String compose(
            Instant windowStart,
            Instant windowEnd,
            int windowDays,
            OperationsSummary summary,
            List<AgentRunSummary> agents,
            List<OperationsAnomaly> anomalies
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 运营概览\n");
        builder.append(String.format(Locale.ROOT, "- 统计窗口：%s → %s（%d 天）%n",
                windowStart, windowEnd, windowDays));
        builder.append(String.format(Locale.ROOT, "- 执行总数：%d（成功 %d / 失败 %d / 待审 %d / 拒绝 %d / 运行中 %d）%n",
                summary.totalRuns(), summary.completed(), summary.failed(),
                summary.pendingApproval(), summary.rejected(), summary.running()));
        builder.append(String.format(Locale.ROOT, "- 成功率：%.1f%%；失败率：%.1f%%（分母只含成功+失败）%n",
                summary.successRate() * 100, summary.failureRate() * 100));
        builder.append(String.format(Locale.ROOT, "- Token：%d（提示 %d / 补全 %d）%n",
                summary.totalTokens(), summary.promptTokens(), summary.completionTokens()));
        builder.append(String.format(Locale.ROOT, "- 估算成本：%.6f USD%s%n", summary.totalCostUsd(),
                summary.costConfigured() ? "" : "（部分模型未配置单价，仅为已配价部分）"));
        if (summary.truncated()) {
            builder.append("- 注意：窗口内记录超过读取上限，统计已截断\n");
        }

        builder.append("\n## 异常与风险\n");
        if (anomalies.isEmpty()) {
            builder.append("- 未命中任何异常规则\n");
        } else {
            for (OperationsAnomaly anomaly : anomalies) {
                builder.append(String.format(Locale.ROOT, "- [%s] %s（%s）%n",
                        anomaly.level().label(), anomaly.message(), anomaly.code().name()));
            }
        }

        builder.append("\n## 建议动作\n");
        if (summary.pendingApproval() > 0) {
            builder.append(String.format(Locale.ROOT, "- 处理 %d 条待审审批：`GET /api/v1/platform/approvals?status=PENDING`%n",
                    summary.pendingApproval()));
        }
        if (summary.failed() > 0) {
            builder.append("- 按失败 Agent 查 `/api/v1/platform/executions` 的 errorMessage 与 traceId\n");
        }
        builder.append("- 如指标与面板不一致，先确认统计窗口与租户范围（admin 看整租户，普通用户只看自己）\n");

        builder.append("\n## Agent 明细\n");
        if (agents.isEmpty()) {
            builder.append("- 窗口内无执行记录\n");
        } else {
            for (AgentRunSummary agent : agents) {
                builder.append(String.format(Locale.ROOT, "- %s：执行 %d、失败 %d、token %d%n",
                        agent.agentKey(), agent.runs(), agent.failures(), agent.tokens()));
            }
        }
        builder.append("\n> 本报告由规则模板生成（模型不可用或未返回内容）。");
        return builder.toString();
    }
}
