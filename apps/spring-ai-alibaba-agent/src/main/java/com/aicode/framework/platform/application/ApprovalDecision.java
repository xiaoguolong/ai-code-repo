package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;

import java.util.List;

/**
 * 审批决策结果（Week 23；Week 24 增加随访任务）。
 *
 * @param approval        决策后的审批任务
 * @param executionStatus 决策后的执行状态
 * @param memoryId        批准时写入的记忆 ID；拒绝或写失败为 null
 * @param followUpTaskIds 批准时生成的随访任务 ID；拒绝或生成失败为空列表
 */
public record ApprovalDecision(
        PlatformApproval approval,
        ExecutionStatus executionStatus,
        String memoryId,
        List<String> followUpTaskIds
) {

    public ApprovalDecision {
        followUpTaskIds = followUpTaskIds == null ? List.of() : List.copyOf(followUpTaskIds);
    }

    /**
     * Week 23 兼容构造器：没有随访任务。
     *
     * @param approval        审批任务
     * @param executionStatus 执行终态
     * @param memoryId        记忆 ID
     */
    public ApprovalDecision(
            PlatformApproval approval,
            ExecutionStatus executionStatus,
            String memoryId
    ) {
        this(approval, executionStatus, memoryId, List.of());
    }
}
