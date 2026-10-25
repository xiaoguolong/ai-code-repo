package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 人工审批任务端口（Week 23）。
 *
 * <p>所有写操作都必须保持「一个 execution 最多一个任务」；决策用
 * {@link #markDecided} 做原子状态迁移，禁止「先查后改」。</p>
 */
public interface PlatformApprovalPort {

    /**
     * 保存审批任务（按 approvalId 覆盖写）。
     *
     * @param approval 审批任务
     */
    void save(PlatformApproval approval);

    /**
     * 按 ID 查询。
     *
     * @param approvalId 审批任务 ID
     * @return 任务；不存在为空
     */
    Optional<PlatformApproval> findById(String approvalId);

    /**
     * 查询某用户的审批任务（按创建时间倒序）。
     *
     * @param userId 发起人用户 ID
     * @param status 状态过滤
     * @param limit  条数上限
     * @return 任务列表，无数据返回空列表
     */
    List<PlatformApproval> listByUser(long userId, ApprovalStatus status, int limit);

    /**
     * 查询全部用户的审批任务（仅管理员运维/待办总览使用）。
     *
     * @param status 状态过滤
     * @param limit  条数上限
     * @return 任务列表，无数据返回空列表
     */
    List<PlatformApproval> listAll(ApprovalStatus status, int limit);

    /**
     * 原子决策：只有当前状态为 {@code PENDING} 才会更新成功。
     *
     * @param approvalId 审批任务 ID
     * @param status     目标状态（APPROVED / REJECTED）
     * @param decidedBy  决策人
     * @param comment    审批意见
     * @param decidedAt  决策时间
     * @return true = 本次抢到决策权；false = 已被决策或不存在
     */
    boolean markDecided(
            String approvalId,
            ApprovalStatus status,
            long decidedBy,
            String comment,
            Instant decidedAt);
}
