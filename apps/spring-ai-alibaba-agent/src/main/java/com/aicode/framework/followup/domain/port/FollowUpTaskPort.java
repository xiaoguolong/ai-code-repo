package com.aicode.framework.followup.domain.port;

import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 随访任务端口（Week 24）。
 *
 * <p>隔离规则写死在实现里：所有查询/迁移都必须带 {@code tenantKey + userId}，
 * 不做「先查后判归属」；状态迁移用原子 {@code WHERE status='PENDING'}。</p>
 */
public interface FollowUpTaskPort {

    /**
     * 保存任务（按 taskId 覆盖写）。
     *
     * @param task 任务
     */
    void save(FollowUpTask task);

    /**
     * 按 ID 查询（不带范围，调用方必须再做归属校验）。
     *
     * @param taskId 任务 ID
     * @return 任务；不存在为空
     */
    Optional<FollowUpTask> findById(String taskId);

    /**
     * 查询任务列表。
     *
     * @param tenantKey 租户
     * @param userId    归属用户
     * @param status    状态过滤（null 表示不过滤）
     * @param patientId 患者过滤（空白表示不过滤）
     * @param limit     条数上限
     * @return 按到期时间升序的任务，无数据返回空列表
     */
    List<FollowUpTask> list(
            String tenantKey, long userId, FollowUpStatus status, String patientId, int limit);

    /**
     * 查询待执行且到期时间落在窗口内的任务（站内提醒用）。
     *
     * @param tenantKey   租户
     * @param userId      归属用户
     * @param fromInclusive 下界（含），null 表示不限
     * @param toExclusive   上界（不含），null 表示不限
     * @param limit       条数上限
     * @return 按到期时间升序的 PENDING 任务
     */
    List<FollowUpTask> listPendingDue(
            String tenantKey, long userId, Instant fromInclusive, Instant toExclusive, int limit);

    /**
     * 查询某审批生成的任务（幂等判重用）。
     *
     * @param approvalId 审批任务 ID
     * @return 任务列表，无数据返回空列表
     */
    List<FollowUpTask> listByApproval(String approvalId);

    /**
     * 原子状态迁移：只有 {@code PENDING} 才会更新成功。
     *
     * @param taskId    任务 ID
     * @param tenantKey 租户（隔离谓词）
     * @param userId    归属用户（隔离谓词）
     * @param target    目标状态（COMPLETED / CANCELLED）
     * @param note      备注
     * @param updatedAt 更新时间
     * @param completedAt 完成时间（仅 COMPLETED 时有值）
     * @return true = 本次迁移成功；false = 不存在、越权或已是终态
     */
    boolean markStatus(
            String taskId,
            String tenantKey,
            long userId,
            FollowUpStatus target,
            String note,
            Instant updatedAt,
            Instant completedAt);

    /**
     * 统计指定状态的待办数（观测与运维巡检用）。
     *
     * @param tenantKey 租户
     * @param userId    归属用户
     * @param status    状态
     * @return 条数
     */
    long countByStatus(String tenantKey, long userId, FollowUpStatus status);
}
