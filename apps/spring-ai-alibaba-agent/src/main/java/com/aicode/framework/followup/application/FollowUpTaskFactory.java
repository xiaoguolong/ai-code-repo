package com.aicode.framework.followup.application;

import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpPlan;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 随访任务创建（Week 28，包内协作类）：Agent 运行落任务 + 审批通过生成任务。
 *
 * <p>落库失败不抛给审批联动路径，但必须留审计；因此
 * {@link #createFromApproval} 返回实际创建成功的任务 ID。幂等：同一审批的同一标题只生成一次。</p>
 */
final class FollowUpTaskFactory {

    private static final Logger log = LoggerFactory.getLogger(FollowUpTaskFactory.class);

    private final FollowUpTaskPort taskPort;
    private final FollowUpPlanner planner;
    private final AuditLogPort auditLogPort;

    FollowUpTaskFactory(
            FollowUpTaskPort taskPort,
            FollowUpPlanner planner,
            AuditLogPort auditLogPort
    ) {
        this.taskPort = taskPort;
        this.planner = planner == null ? new FollowUpPlanner() : planner;
        this.auditLogPort = auditLogPort;
    }

    /**
     * 按 Agent 计划落任务（scope 为空时由调用方跳过）。
     *
     * @param plan      计划结果
     * @param tenantKey 租户
     * @param userId    归属用户
     * @param patientId 患者
     * @param now       创建时间
     * @return 已创建任务
     */
    List<FollowUpTask> createFromPlan(
            FollowUpPlanResult plan,
            String tenantKey,
            long userId,
            String patientId,
            Instant now
    ) {
        List<FollowUpTask> saved = new ArrayList<>();
        for (FollowUpPlanItem item : plan.tasks()) {
            FollowUpTask created = newTask(
                    item, tenantKey, userId, patientId, FollowUpSource.AGENT, null, null, now);
            taskPort.save(created);
            audit(userId, AuditAction.FOLLOWUP_TASK_CREATE, AuditResult.SUCCESS, created);
            saved.add(created);
        }
        log.info("[followup] tasks created patientId={} userId={} count={}",
                patientId, userId, saved.size());
        return saved;
    }

    /**
     * 审批通过后生成随访任务（幂等：同一审批的同一标题只生成一次）。
     *
     * @param approval 已批准的审批任务
     * @param now      决策时间
     * @return 本次实际创建的任务 ID（已存在的不重复创建）
     */
    List<String> createFromApproval(PlatformApproval approval, Instant now) {
        FollowUpPlan plan = planner.plan(approval.riskLevel(), now);
        Set<String> existingTitles = new HashSet<>();
        for (FollowUpTask task : taskPort.listByApproval(approval.approvalId())) {
            existingTitles.add(task.title());
        }
        List<String> createdIds = new ArrayList<>();
        for (FollowUpPlanItem item : plan.items()) {
            if (existingTitles.contains(item.title())) {
                continue;
            }
            FollowUpTask task = newTask(
                    item, approval.tenantKey(), approval.userId(), approval.patientId(),
                    FollowUpSource.APPROVAL, approval.approvalId(), approval.executionId(), now);
            try {
                taskPort.save(task);
                audit(approval.userId(), AuditAction.FOLLOWUP_TASK_CREATE, AuditResult.SUCCESS, task);
                createdIds.add(task.taskId());
            } catch (RuntimeException ex) {
                // 唯一索引（approval_id + title）或数据库故障：审批已经生效，不能因此失败。
                log.warn("[followup] task create failed approvalId={} title={} error={}",
                        approval.approvalId(), item.title(), ex.getMessage());
                audit(approval.userId(), AuditAction.FOLLOWUP_TASK_CREATE, AuditResult.FAILURE, task);
            }
        }
        log.info("[followup] tasks from approval approvalId={} userId={} created={}",
                approval.approvalId(), approval.userId(), createdIds.size());
        return createdIds;
    }

    private FollowUpTask newTask(
            FollowUpPlanItem item,
            String tenantKey,
            long userId,
            String patientId,
            FollowUpSource source,
            String approvalId,
            String executionId,
            Instant now
    ) {
        return new FollowUpTask(
                UUID.randomUUID().toString(), tenantKey, userId, patientId, source,
                approvalId, executionId, item.title(), item.detail(), item.priority(),
                FollowUpStatus.PENDING, item.dueAt(), now, now, null, "");
    }

    private void audit(long userId, AuditAction action, AuditResult result, FollowUpTask task) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, action, "follow-up:" + task.taskId(), result, TraceIds.current(),
                    "taskId=" + task.taskId() + " patientId=" + task.patientId()
                            + " source=" + task.source().name()
                            + (task.approvalId() == null ? "" : " approvalId=" + task.approvalId())));
        } catch (RuntimeException ex) {
            log.warn("[followup] audit record failed action={} taskId={} error={}",
                    action, task.taskId(), ex.getMessage());
        }
    }
}
