package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;

/**
 * 审批决策结果（Week 23）。
 *
 * @param approval        决策后的审批任务
 * @param executionStatus 决策后的执行状态
 * @param memoryId        批准时写入的记忆 ID；拒绝或写失败为 null
 */
public record ApprovalDecision(
        PlatformApproval approval,
        ExecutionStatus executionStatus,
        String memoryId
) {
}
