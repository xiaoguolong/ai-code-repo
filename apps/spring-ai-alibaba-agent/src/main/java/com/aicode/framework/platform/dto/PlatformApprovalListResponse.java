package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.application.ApprovalDecision;
import com.aicode.framework.platform.domain.model.PlatformApproval;

import java.time.Instant;
import java.util.List;

/**
 * 审批待办列表响应（Week 23）。
 *
 * @param items 当前页任务
 * @param total 当前页条数
 */
public record PlatformApprovalListResponse(List<PlatformApprovalResponse> items, int total) {

    /**
     * 由领域模型列表转换。
     *
     * @param approvals 审批任务列表
     * @return 响应体
     */
    public static PlatformApprovalListResponse from(List<PlatformApproval> approvals) {
        List<PlatformApprovalResponse> items = approvals.stream()
                .map(PlatformApprovalResponse::from)
                .toList();
        return new PlatformApprovalListResponse(items, items.size());
    }

    /**
     * 审批决策响应（Week 23）。
     *
     * @param approvalId      审批任务 ID
     * @param executionId     执行记录 ID
     * @param status          审批终态
     * @param decidedBy       决策人
     * @param comment         审批意见
     * @param decidedAt       决策时间
     * @param executionStatus 执行终态
     * @param memoryId        批准时写入的记忆 ID；拒绝或写失败为 null
     * @param followUpTaskIds 批准时生成的随访任务 ID（Week 24）
     */
    public record Decision(
            String approvalId,
            String executionId,
            String status,
            Long decidedBy,
            String comment,
            Instant decidedAt,
            String executionStatus,
            String memoryId,
            List<String> followUpTaskIds
    ) {

        /**
         * 由用例结果转换。
         *
         * @param decision 决策结果
         * @return 响应体
         */
        public static Decision from(ApprovalDecision decision) {
            PlatformApproval approval = decision.approval();
            return new Decision(
                    approval.approvalId(),
                    approval.executionId(),
                    approval.status().name(),
                    approval.decidedBy(),
                    approval.comment(),
                    approval.decidedAt(),
                    decision.executionStatus().name(),
                    decision.memoryId(),
                    decision.followUpTaskIds());
        }
    }
}
