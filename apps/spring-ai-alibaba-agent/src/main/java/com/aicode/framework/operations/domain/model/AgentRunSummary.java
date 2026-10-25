package com.aicode.framework.operations.domain.model;

/**
 * 按 Agent 分组的执行统计（Week 24）。
 *
 * @param agentKey Agent 标识
 * @param runs     执行数
 * @param failures 失败数
 * @param tokens   token 合计
 */
public record AgentRunSummary(String agentKey, long runs, long failures, long tokens) {

    /** @return 该 Agent 的失败率（分母为 0 时返回 0） */
    public double failureRate() {
        return runs <= 0 ? 0.0 : (double) failures / runs;
    }
}
