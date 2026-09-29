package com.aicode.framework.workflow.domain.exception;

/**
 * 找不到指定 workflowId 的运行记录（checkpoint 不存在）。
 */
public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(String workflowId) {
        super("workflow not found: " + workflowId);
    }
}
