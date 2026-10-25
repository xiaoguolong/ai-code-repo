package com.aicode.framework.followup.application;

import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 随访任务状态迁移用例（Week 24）：完成 / 取消。
 *
 * <p>与 {@link FollowUpTaskUseCase}（生成与查询）拆开：本类只处理状态机与副作用，
 * 单一职责，也避免单类超过 200 行。</p>
 *
 * <p>并发：先做归属校验，再用 {@link FollowUpTaskPort#markStatus} 的
 * {@code WHERE status='PENDING'} 原子迁移；抢输方得到 409，
 * 不会出现「已完成又被取消」。</p>
 */
@Service
public class FollowUpTaskActionUseCase {

    private static final Logger log = LoggerFactory.getLogger(FollowUpTaskActionUseCase.class);

    private final FollowUpTaskUseCase taskUseCase;
    private final FollowUpTaskPort taskPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public FollowUpTaskActionUseCase(
            FollowUpTaskUseCase taskUseCase,
            FollowUpTaskPort taskPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            Clock clock
    ) {
        this.taskUseCase = taskUseCase;
        this.taskPort = taskPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 完成任务。
     *
     * @param callerUserId 调用者
     * @param taskId       任务 ID
     * @param note         完成备注（可空，最长 500）
     * @return 完成后的任务
     * @throws com.aicode.framework.platform.domain.exception.PlatformNotFoundException 不存在
     * @throws com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException 越权
     * @throws PlatformConflictException 已是终态
     */
    public FollowUpTask complete(long callerUserId, String taskId, String note) {
        return transition(callerUserId, taskId, FollowUpStatus.COMPLETED, note);
    }

    /**
     * 取消任务。
     *
     * @param callerUserId 调用者
     * @param taskId       任务 ID
     * @param reason       取消原因（可空，最长 500）
     * @return 取消后的任务
     * @throws com.aicode.framework.platform.domain.exception.PlatformNotFoundException 不存在
     * @throws com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException 越权
     * @throws PlatformConflictException 已是终态
     */
    public FollowUpTask cancel(long callerUserId, String taskId, String reason) {
        return transition(callerUserId, taskId, FollowUpStatus.CANCELLED, reason);
    }

    private FollowUpTask transition(
            long callerUserId, String taskId, FollowUpStatus target, String note) {
        FollowUpTask task = taskUseCase.requireTask(taskId);
        permissionChecker.requireFollowUpAccess(callerUserId, task.userId());
        if (!task.pending()) {
            throw new PlatformConflictException("follow-up task is not pending: " + task.taskId());
        }
        String normalizedNote = FollowUpTaskUseCase.normalizeNote(note);
        Instant now = clock.instant();
        Instant completedAt = target == FollowUpStatus.COMPLETED ? now : null;
        boolean changed = taskPort.markStatus(
                task.taskId(), task.tenantKey(), task.userId(), target, normalizedNote, now, completedAt);
        if (!changed) {
            throw new PlatformConflictException("follow-up task is not pending: " + task.taskId());
        }
        FollowUpTask updated = taskUseCase.requireTask(task.taskId());
        audit(callerUserId, target, normalizedNote, updated);
        log.info("[followup] task transitioned taskId={} userId={} target={}",
                task.taskId(), callerUserId, target);
        return updated;
    }

    private void audit(long userId, FollowUpStatus target, String note, FollowUpTask task) {
        AuditAction action = target == FollowUpStatus.COMPLETED
                ? AuditAction.FOLLOWUP_TASK_COMPLETE : AuditAction.FOLLOWUP_TASK_CANCEL;
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, action, "follow-up:" + task.taskId(), AuditResult.SUCCESS,
                    TraceIds.current(),
                    "taskId=" + task.taskId() + " patientId=" + task.patientId()
                            + " chars=" + (note == null ? 0 : note.length())));
        } catch (RuntimeException ex) {
            log.warn("[followup] audit record failed action={} taskId={} error={}",
                    action, task.taskId(), ex.getMessage());
        }
    }
}
