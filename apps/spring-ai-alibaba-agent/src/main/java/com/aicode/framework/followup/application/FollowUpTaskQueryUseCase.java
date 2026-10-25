package com.aicode.framework.followup.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 随访任务查询（Week 28，包内协作类）：列表 / 详情 / 站内提醒 / 范围解析。
 *
 * <p>与生成/状态迁移拆开：本类只读，不写任务、不调模型。隔离规则与 Week 24 一致：
 * 所有查询带 {@code tenantKey + userId}；admin 可查他人；租户键只从用户记录推导。</p>
 */
final class FollowUpTaskQueryUseCase {

    private final FollowUpTaskPort taskPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformFollowUpProperties properties;
    private final Clock clock;

    FollowUpTaskQueryUseCase(
            FollowUpTaskPort taskPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            PlatformFollowUpProperties properties,
            Clock clock
    ) {
        this.taskPort = taskPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.properties = properties == null ? new PlatformFollowUpProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    List<FollowUpTask> list(
            long callerUserId,
            Long targetUserId,
            FollowUpStatus status,
            String patientId,
            int limit
    ) {
        PlatformMemoryScope scope = resolveScope(callerUserId, targetUserId);
        return taskPort.list(scope.tenantKey(), scope.userId(), status, patientId, normalizeLimit(limit));
    }

    FollowUpTask get(long callerUserId, String taskId) {
        FollowUpTask task = requireTask(taskId);
        permissionChecker.requireFollowUpAccess(callerUserId, task.userId());
        return task;
    }

    /**
     * 站内提醒：逾期 + 未来 N 天内到期的待办（只看自己）。
     *
     * @param callerUserId 调用者
     * @param days         未来天数；&le;0 用默认值，超过上限 400
     * @return 提醒视图
     * @throws InvalidChatRequestException days 越界
     */
    FollowUpReminderView reminders(long callerUserId, int days) {
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

    PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
        final long effectiveUserId;
        if (targetUserId != null && targetUserId != callerUserId) {
            if (!permissionChecker.isAdmin(callerUserId)) {
                throw new PlatformAccessDeniedException(
                        "follow-up task access denied for user: " + targetUserId);
            }
            effectiveUserId = targetUserId;
        } else {
            effectiveUserId = callerUserId;
        }
        PlatformUser user = userPort.findById(effectiveUserId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + effectiveUserId));
        return PlatformMemoryScope.of(user);
    }

    FollowUpTask requireTask(String taskId) {
        String normalized = requireText(taskId, "taskId");
        return taskPort.findById(normalized)
                .orElseThrow(() -> new PlatformNotFoundException("follow-up task not found: " + normalized));
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
}
