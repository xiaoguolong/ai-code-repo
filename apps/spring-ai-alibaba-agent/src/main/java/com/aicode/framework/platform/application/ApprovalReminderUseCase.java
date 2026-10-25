package com.aicode.framework.platform.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformApprovalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 审批待办超时提醒用例（Week 27）。
 *
 * <p><b>只提醒，不决策</b>：阈值（默认 24h）到了就记录提醒（审计 + 指标 + 冷却记账），
 * 但审批状态、决策人、执行状态一个都不改。HIGH 风险患者分析宁可积压，
 * 也不能被系统静默放行或关闭。</p>
 *
 * <p>冷却（默认 12h）避免每小时重复提醒同一条待办；站内视图与调度解耦，
 * 运营人员随时能看到积压，不依赖调度是否已跑。</p>
 */
@Service
public class ApprovalReminderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApprovalReminderUseCase.class);

    /** 视图默认条数。 */
    public static final int DEFAULT_VIEW_LIMIT = 20;
    /** 视图硬上限。 */
    public static final int MAX_VIEW_LIMIT = 100;

    private final PlatformApprovalPort approvalPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final AgentObservabilityPort observability;
    private final PlatformApprovalProperties properties;
    private final Clock clock;

    public ApprovalReminderUseCase(
            PlatformApprovalPort approvalPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            AgentObservabilityPort observability,
            PlatformApprovalProperties properties,
            Clock clock
    ) {
        this.approvalPort = approvalPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.observability = observability;
        this.properties = properties == null ? new PlatformApprovalProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 下发一轮超时提醒（调度入口）。
     *
     * <p>单条失败只记 WARN，不中断整轮；返回实际提醒条数。</p>
     *
     * @return 本轮提醒条数；提醒关闭时为 0
     */
    public int remindDue() {
        PlatformApprovalProperties.Reminder reminder = properties.getReminder();
        if (!reminder.isEnabled()) {
            return 0;
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(reminder.resolvedThresholdHours(), ChronoUnit.HOURS);
        Instant remindedBefore = now.minus(reminder.resolvedCooldownHours(), ChronoUnit.HOURS);
        List<PlatformApproval> due = approvalPort.listRemindable(
                cutoff, remindedBefore, reminder.resolvedLimit());
        int reminded = 0;
        for (PlatformApproval approval : due) {
            try {
                approvalPort.markReminded(approval.approvalId(), now);
                audit(approval, now);
                observability.recordCounter(MonitoringMetricNames.APPROVAL_REMINDER_COUNT, 1);
                reminded++;
            } catch (RuntimeException ex) {
                log.warn("[platform] approval reminder failed approvalId={} error={}",
                        approval.approvalId(), ex.getMessage());
            }
        }
        if (reminded > 0) {
            log.warn("[platform] approval reminders sent count={} thresholdHours={} cooldownHours={}",
                    reminded, reminder.resolvedThresholdHours(), reminder.resolvedCooldownHours());
        }
        return reminded;
    }

    /**
     * 查询超时待办视图（本人；admin 为全部）。
     *
     * @param callerUserId 调用者
     * @param limit        条数上限（默认 20、上限 100）
     * @return 超时待办视图
     */
    public ApprovalReminderView view(long callerUserId, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        Instant now = clock.instant();
        int thresholdHours = properties.getReminder().resolvedThresholdHours();
        Instant cutoff = now.minus(thresholdHours, ChronoUnit.HOURS);
        List<PlatformApproval> pending = permissionChecker.isAdmin(callerUserId)
                ? approvalPort.listAllPendingCreatedBefore(cutoff, effectiveLimit)
                : approvalPort.listPendingCreatedBefore(callerUserId, cutoff, effectiveLimit);
        List<ApprovalReminderView.Item> items = pending.stream()
                .map(approval -> new ApprovalReminderView.Item(
                        approval, overdueHours(approval.createdAt(), now)))
                .toList();
        return new ApprovalReminderView(thresholdHours, now, items);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_VIEW_LIMIT;
        }
        return Math.min(limit, MAX_VIEW_LIMIT);
    }

    private long overdueHours(Instant createdAt, Instant now) {
        if (createdAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(createdAt, now).toHours());
    }

    private void audit(PlatformApproval approval, Instant now) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    0L, AuditAction.APPROVAL_REMIND, "approval:" + approval.approvalId(),
                    AuditResult.SUCCESS, TraceIds.current(),
                    "approvalId=" + approval.approvalId()
                            + " overdueHours=" + overdueHours(approval.createdAt(), now)));
        } catch (RuntimeException ex) {
            log.warn("[platform] approval reminder audit failed approvalId={} error={}",
                    approval.approvalId(), ex.getMessage());
        }
    }
}
