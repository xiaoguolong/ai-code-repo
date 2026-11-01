package com.aicode.framework.platform.domain.model;

/**
 * 执行记录状态。
 */
public enum ExecutionStatus {

    /** 运行中 */
    RUNNING,
    /** 成功完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /**
     * 已完成分析、等待人工审核（Week 23）。
     *
     * <p>HIGH 风险患者分析不直接写随访记忆；报告已生成，审批任务在
     * {@code platform_approval} 里，进程重启不影响待办。</p>
     */
    PENDING_APPROVAL,
    /** 人工审核拒绝，不写随访记忆（Week 23） */
    REJECTED,

    /**
     * 调用方取消（Week 31）：用户点"停止"、SSE 断连或超时。
     *
     * <p>不算失败：不计入失败率，也不写业务副作用；记录保留以便追查"谁在什么时候中止了哪次执行"。</p>
     */
    CANCELLED
}
