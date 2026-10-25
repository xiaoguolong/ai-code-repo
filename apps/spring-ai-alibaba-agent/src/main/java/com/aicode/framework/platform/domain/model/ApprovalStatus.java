package com.aicode.framework.platform.domain.model;

/**
 * 人工审批任务状态（Week 23）。
 *
 * <p>与 {@link ExecutionStatus} 的对应关系：`PENDING` ↔ `PENDING_APPROVAL`，
 * `APPROVED` ↔ `COMPLETED`，`REJECTED` ↔ `REJECTED`。两者分开建模是因为
 * 审批是「人的待办」，执行是「Agent 的运行日志」，生命周期与查询维度不同。</p>
 */
public enum ApprovalStatus {

    /** 等待人工决策 */
    PENDING,
    /** 人工批准（已写随访记忆） */
    APPROVED,
    /** 人工拒绝（不写随访记忆） */
    REJECTED
}
