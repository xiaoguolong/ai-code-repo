package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformApproval;

import java.time.Instant;

/**
 * 审批任务响应（Week 23，列表/详情共用）。
 *
 * <p>不返回报告正文：正文在 {@code execution_record.output_json} 里，按 executionId
 * 走既有执行记录接口取，避免同一份患者报告存两份。</p>
 *
 * @param approvalId  审批任务 ID
 * @param executionId 关联执行记录 ID
 * @param agentKey    Agent 标识
 * @param patientId   患者编号
 * @param riskLevel   风险等级
 * @param task        任务原文
 * @param status      审批状态
 * @param comment     审批意见
 * @param createdAt   创建时间
 * @param decidedAt   决策时间；待审为 null
 */
public record PlatformApprovalResponse(
        String approvalId,
        String executionId,
        String agentKey,
        String patientId,
        String riskLevel,
        String task,
        String status,
        String comment,
        Instant createdAt,
        Instant decidedAt
) {

    /**
     * 由领域模型转换。
     *
     * @param approval 审批任务
     * @return 响应体
     */
    public static PlatformApprovalResponse from(PlatformApproval approval) {
        return new PlatformApprovalResponse(
                approval.approvalId(),
                approval.executionId(),
                approval.agentKey(),
                approval.patientId(),
                approval.riskLevel(),
                approval.task(),
                approval.status().name(),
                approval.comment(),
                approval.createdAt(),
                approval.decidedAt());
    }
}
