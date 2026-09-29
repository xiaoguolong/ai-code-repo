package com.aicode.framework.workflow.domain.model;

/**
 * 患者风险 Workflow 运行状态。
 */
public enum WorkflowStatus {

    /** 流程已正常完成（含报告）。 */
    COMPLETED,

    /** 高风险任务暂停，等待人工审核。 */
    PENDING_APPROVAL,

    /** 人工审核拒绝，流程终止。 */
    REJECTED
}
