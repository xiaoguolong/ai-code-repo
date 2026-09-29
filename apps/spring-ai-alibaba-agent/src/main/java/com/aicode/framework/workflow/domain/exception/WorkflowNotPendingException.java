package com.aicode.framework.workflow.domain.exception;

/**
 * 指定 workflow 不处于待人工审核状态，无法 resume。
 */
public class WorkflowNotPendingException extends RuntimeException {

    public WorkflowNotPendingException(String workflowId) {
        super("workflow is not pending approval: " + workflowId);
    }
}
