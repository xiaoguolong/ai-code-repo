package com.aicode.framework.followup.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.followup.infrastructure.FollowUpAgent;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 随访任务用例（Week 24，Agent 4；Week 28 拆分为门面）。
 *
 * <p>内部协作类：{@link FollowUpTaskFactory}（Agent 计划/审批通过 → 落任务 + 审计）、
 * {@link FollowUpTaskQueryUseCase}（列表/详情/提醒/范围）。本类保留原构造器与公共方法，
 * 控制器与审批联动不改。</p>
 */
@Service
public class FollowUpTaskUseCase {

    /** 任务备注长度上限。 */
    public static final int MAX_NOTE_LENGTH = 500;

    private final FollowUpAgent followUpAgent;
    private final FollowUpTaskFactory factory;
    private final FollowUpTaskQueryUseCase queryUseCase;
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
        this.factory = new FollowUpTaskFactory(taskPort, planner, auditLogPort);
        this.queryUseCase = new FollowUpTaskQueryUseCase(
                taskPort, userPort, permissionChecker, properties, clock);
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
        List<FollowUpTask> saved = factory.createFromPlan(
                plan, scope.tenantKey(), scope.userId(), normalizedPatientId, now);
        return new FollowUpRunResult(plan, saved);
    }

    /**
     * 审批通过后生成随访任务（幂等：同一审批的同一标题只生成一次）。
     *
     * @param approval 已批准的审批任务
     * @param now      决策时间
     * @return 本次实际创建的任务 ID
     */
    public List<String> createFromApproval(PlatformApproval approval, Instant now) {
        return factory.createFromApproval(approval, now);
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
     */
    public List<FollowUpTask> list(
            long callerUserId, Long targetUserId, FollowUpStatus status, String patientId, int limit) {
        return queryUseCase.list(callerUserId, targetUserId, status, patientId, limit);
    }

    /**
     * 查询任务详情（本人或 admin）。
     *
     * @param callerUserId 调用者
     * @param taskId       任务 ID
     * @return 任务
     */
    public FollowUpTask get(long callerUserId, String taskId) {
        return queryUseCase.get(callerUserId, taskId);
    }

    /**
     * 站内提醒：逾期 + 未来 N 天内到期的待办（只看自己）。
     *
     * @param callerUserId 调用者
     * @param days         未来天数；&le;0 用默认值
     * @return 提醒视图
     */
    public FollowUpReminderView reminders(long callerUserId, int days) {
        return queryUseCase.reminders(callerUserId, days);
    }

    /**
     * 解析可访问范围（供控制器回显 tenantKey / userId）。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可传）
     * @return 范围
     */
    public PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
        return queryUseCase.resolveScope(callerUserId, targetUserId);
    }

    /**
     * 按 ID 取任务（不做归属校验，调用方必须自行校验）。
     *
     * @param taskId 任务 ID
     * @return 任务
     */
    public FollowUpTask requireTask(String taskId) {
        return queryUseCase.requireTask(taskId);
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

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return normalized;
    }
}
