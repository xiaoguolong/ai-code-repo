package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 患者分析人工审批任务（Week 23）。
 *
 * <p>一个任务对应一次 `execution_record`（{@code executionId} 唯一）；报告正文不在此冗余，
 * 决策时从执行记录的 `output_json` 读取，避免同一份患者报告存两处后不一致。</p>
 *
 * @param approvalId  审批任务 ID
 * @param executionId 关联的执行记录 ID（唯一）
 * @param tenantKey   归属租户（由用户记录推导，不接受客户端传入）
 * @param userId      任务发起人（也是默认审批人）
 * @param agentKey    Agent 标识
 * @param patientId   患者编号
 * @param riskLevel   风险等级（HIGH 才会生成审批任务）
 * @param task        归一化后的任务原文
 * @param status      审批状态
 * @param comment     审批意见，可为空串
 * @param decidedBy   决策人用户 ID；待审时为 null
 * @param createdAt   创建时间
 * @param decidedAt   决策时间；待审时为 null
 */
public record PlatformApproval(
        String approvalId,
        String executionId,
        String tenantKey,
        long userId,
        String agentKey,
        String patientId,
        String riskLevel,
        String task,
        ApprovalStatus status,
        String comment,
        Long decidedBy,
        Instant createdAt,
        Instant decidedAt
) {

    public PlatformApproval {
        comment = comment == null ? "" : comment;
        task = task == null ? "" : task;
    }

    /**
     * @return 是否仍可被决策
     */
    public boolean pending() {
        return status == ApprovalStatus.PENDING;
    }
}
