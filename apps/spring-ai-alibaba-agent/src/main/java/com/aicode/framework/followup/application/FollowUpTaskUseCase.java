package com.aicode.framework.followup.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpPlan;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.followup.infrastructure.FollowUpAgent;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 随访任务用例（Week 24，Agent 4）：生成计划落任务、列表/详情、站内提醒、审批联动。
 *
 * <p>隔离规则（写死并单测）：所有查询都带 {@code tenantKey + userId}；admin 可以看他人，
 * 非 admin 只能看自己；租户键只从用户记录推导。</p>
 *
 * <p>落库失败不抛给调用方（审批联动路径），但必须留审计；因此
 * {@link #createFromApproval} 返回实际创建成功的任务 ID 列表。</p>
 */
@Service
public class FollowUpTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(FollowUpTaskUseCase.class);

    /** 任务备注长度上限。 */
    public static final int MAX_NOTE_LENGTH = 500;

    private final FollowUpAgent followUpAgent;
    private final FollowUpPlanner planner;
    private final FollowUpTaskPort taskPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final PlatformFollowUpProperties properties;
    private final Clock clock;

    public FollowUpTaskUseCase(
            FollowUpAgent followUpAgent,
            FollowUpPlanner planner,
            FollowUpTaskPort taskPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            PlatformFollowUpProperties properties,
            Clock clock
    ) {
        this.followUpAgent = followUpAgent;
        this.planner = planner == null ? new FollowUpPlanner() : planner;
        this.taskPort = taskPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.properties = properties == null ? new PlatformFollowUpProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 运行随访 Agent 并（在有运行范围时）落任务。
     *
     * @param patientId 患者编号
     * @param task      任务原文，可为空
     * @param scope     记忆范围（租户+用户）；为空（直连 Run）时不落库
     * @return 计划结果 + 已落库任务
     * @throws InvalidChatRequestException patientId 空白
     */
    public FollowUpRunResult run(String patientId, String task, PlatformMemoryScope scope) {
        String normalizedPatientId = requireText(patientId, "patientId");
        FollowUpPlanResult plan = followUpAgent.run(normalizedPatientId, task);
        if (scope == null) {
            return new FollowUpRunResult(plan, List.of());
        }
        Instant now = clock.instant();
        List<FollowUpTask> saved = new ArrayList<>();
        for (FollowUpPlanItem item : plan.tasks()) {
            FollowUpTask created = newTask(
                    item, scope.tenantKey(), scope.userId(), normalizedPatientId,
                    FollowUpSource.AGENT, null, null, now);
            taskPort.save(created);
            audit(scope.userId(), AuditAction.FOLLOWUP_TASK_CREATE, AuditResult.SUCCESS, created);
            saved.add(created);
        }
        log.info("[followup] tasks created patientId={} userId={} count={}",
                normalizedPatientId, scope.userId(), saved.size());
        return new FollowUpRunResult(plan, saved);
    }

    /**
     * 审批通过后生成随访任务（幂等：同一审批的同一标题只生成一次）。
     *
     * @param approval 已批准的审批任务
     * @param now      决策时间
     * @return 本次实际创建的任务 ID（已存在的不重复创建）
     */
    public List<String> createFromApproval(PlatformApproval approval, Instant now) {
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

    /**
     * 查询任务列表。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可传；null 表示查自己）
     * @param status       状态过滤（null 表示不过滤）
     * @param patientId    患者过滤
     * @param limit        条数上限
     * @return 任务列表
     * @throws PlatformAccessDeniedException 非 admin 指定他人
     * @throws PlatformNotFoundException     目标用户不存在
     */
    public List<FollowUpTask> list(
            long callerUserId, Long targetUserId, FollowUpStatus status, String patientId, int limit) {
        PlatformMemoryScope scope = resolveScope(callerUserId, targetUserId);
        return taskPort.list(
                scope.tenantKey(), scope.userId(), status, patientId, normalizeLimit(limit));
    }

    /**
     * 查询任务详情（本人或 admin）。
     *
     * @param callerUserId 调用者
     * @param taskId       任务 ID
     * @return 任务
     * @throws PlatformNotFoundException     不存在
     * @throws PlatformAccessDeniedException 越权
     */
    public FollowUpTask get(long callerUserId, String taskId) {
        FollowUpTask task = requireTask(taskId);
        permissionChecker.requireFollowUpAccess(callerUserId, task.userId());
        return task;
    }

    /**
     * 站内提醒：逾期 + 未来 N 天内到期的待办（只看自己）。
     *
     * @param callerUserId 调用者
     * @param days         未来天数；&lt;=0 用默认值，超过上限 400
     * @return 提醒视图
     * @throws InvalidChatRequestException days 越界
     */
    public FollowUpReminderView reminders(long callerUserId, int days) {
        int effectiveDays = resolveReminderDays(days);
        PlatformMemoryScope scope = resolveScope(callerUserId, null);
        Instant now = clock.instant();
        Instant to = now.plus(effectiveDays, ChronoUnit.DAYS);
        int limit = properties.resolvedMaxListLimit();
        List<FollowUpTask> overdue = taskPort.listPendingDue(
                scope.tenantKey(), scope.userId(), null, now, limit);
        List<FollowUpTask> dueSoon = taskPort.listPendingDue(
                scope.tenantKey(), scope.userId(), now, to, limit);
        return new FollowUpReminderView(effectiveDays, overdue, dueSoon);
    }

    /**
     * 解析可访问范围（供控制器回显 tenantKey / userId）。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可传）
     * @return 范围
     */
    public PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
        final long effectiveUserId;
        if (targetUserId != null && targetUserId != callerUserId) {
            if (!permissionChecker.isAdmin(callerUserId)) {
                throw new PlatformAccessDeniedException("follow-up task access denied for user: " + targetUserId);
            }
            effectiveUserId = targetUserId;
        } else {
            effectiveUserId = callerUserId;
        }
        PlatformUser user = userPort.findById(effectiveUserId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + effectiveUserId));
        return PlatformMemoryScope.of(user);
    }

    /**
     * 按 ID 取任务（不做归属校验，调用方必须自行校验）。
     *
     * @param taskId 任务 ID
     * @return 任务
     * @throws PlatformNotFoundException 不存在
     */
    public FollowUpTask requireTask(String taskId) {
        String normalized = requireText(taskId, "taskId");
        return taskPort.findById(normalized)
                .orElseThrow(() -> new PlatformNotFoundException("follow-up task not found: " + normalized));
    }

    /**
     * 规范化备注：空白转空串，超长 400。
     *
     * @param note 备注
     * @return 规范化后的备注
     * @throws InvalidChatRequestException 超长
     */
    public static String normalizeNote(String note) {
        if (note == null) {
            return "";
        }
        String normalized = note.trim();
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw new InvalidChatRequestException("note must not exceed " + MAX_NOTE_LENGTH + " characters");
        }
        return normalized;
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

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return properties.resolvedDefaultListLimit();
        }
        return Math.min(limit, properties.resolvedMaxListLimit());
    }

    private int resolveReminderDays(int days) {
        int max = properties.resolvedMaxReminderDays();
        if (days <= 0) {
            return properties.resolvedDefaultReminderDays();
        }
        if (days > max) {
            throw new InvalidChatRequestException("days must be between 1 and " + max);
        }
        return days;
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return normalized;
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
