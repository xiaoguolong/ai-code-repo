package com.aicode.framework.operations.domain.model;

/**
 * 运营异常规则码（Week 24）。
 *
 * <p>规则码是稳定契约：日报、审计、面板与排查脚本都按它检索，**禁止随手改名**。
 * 新增规则必须同步 {@code docs/specs/week-24.md} 第 2.2 节与
 * {@code OperationsAnomalyDetectorTest}。</p>
 */
public enum OperationsAnomalyCode {

    /** 失败率 ≥ 高阈值（默认 30%）。 */
    FAILURE_RATE_HIGH,
    /** 失败率 ≥ 中阈值（默认 20%）但未到高阈值。 */
    FAILURE_RATE_ELEVATED,
    /** 待审审批数超过积压阈值（默认 20）。 */
    APPROVAL_BACKLOG,
    /** 本窗口 token 超过上一等长窗口的倍数阈值（默认 2 倍）。 */
    TOKEN_SPIKE,
    /** 单个 Agent 失败次数达到阈值且失败占比过半。 */
    AGENT_FAILURE_CONCENTRATION,
    /** 窗口内没有任何执行记录。 */
    NO_TRAFFIC
}
