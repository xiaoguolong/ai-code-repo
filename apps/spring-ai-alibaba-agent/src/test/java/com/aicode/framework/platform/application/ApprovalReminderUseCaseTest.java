package com.aicode.framework.platform.application;

import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformApprovalProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 审批待办超时提醒用例（Week 27）。
 *
 * <p>锁定的关键语义：只提醒不决策、阈值/冷却生效、admin 可见全量、单条失败不中断。</p>
 */
class ApprovalReminderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T18:00:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long OTHER_USER_ID = 3L;

    private InMemoryPlatformApprovalAdapter approvals;
    private InMemoryAuditLogAdapter auditLog;
    private RecordingObservabilityPort observability;
    private PlatformApprovalProperties properties;
    private PlatformPermissionChecker checker;

    @BeforeEach
    void setUp() {
        approvals = new InMemoryPlatformApprovalAdapter();
        auditLog = new InMemoryAuditLogAdapter();
        observability = new RecordingObservabilityPort();
        properties = new PlatformApprovalProperties();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(OTHER_USER_ID, "other", "hash", "operator"));
        checker = new PlatformPermissionChecker(users, roles);
    }

    @Test
    void remindsOnlyPendingOlderThanThreshold() {
        approvals.save(approval("old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(25 * 3600)));
        approvals.save(approval("fresh", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(2 * 3600)));
        approvals.save(approval("decided", OPERATOR_ID, ApprovalStatus.APPROVED, NOW.minusSeconds(25 * 3600)));
        ApprovalReminderUseCase useCase = useCase(NOW);

        int reminded = useCase.remindDue();

        assertThat(reminded).isEqualTo(1);
        assertThat(auditLog.listAll())
                .extracting(AuditLogEntry::action)
                .containsExactly(AuditAction.APPROVAL_REMIND);
        assertThat(auditLog.listAll().get(0).userId()).isZero();
        assertThat(auditLog.listAll().get(0).detail()).contains("approvalId=old", "overdueHours=25");
        assertThat(observability.calls).contains("recordCounter:approval.reminder.count");
    }

    @Test
    void reminderDoesNotChangeApprovalState() {
        approvals.save(approval("old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(25 * 3600)));

        useCase(NOW).remindDue();

        PlatformApproval loaded = approvals.findById("old").orElseThrow();
        assertThat(loaded.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(loaded.decidedBy()).isNull();
        assertThat(loaded.decidedAt()).isNull();
        assertThat(loaded.comment()).isEmpty();
    }

    @Test
    void cooldownPreventsDuplicateReminder() {
        approvals.save(approval("old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(25 * 3600)));

        assertThat(useCase(NOW).remindDue()).isEqualTo(1);
        assertThat(useCase(NOW.plusSeconds(3600)).remindDue()).isZero();
        assertThat(useCase(NOW.plusSeconds(13 * 3600)).remindDue()).isEqualTo(1);
    }

    @Test
    void disabledReminderDoesNothing() {
        properties.getReminder().setEnabled(false);
        approvals.save(approval("old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(25 * 3600)));

        assertThat(useCase(NOW).remindDue()).isZero();
        assertThat(auditLog.listAll()).isEmpty();
    }

    @Test
    void thresholdIsClampedToAtLeastOneHour() {
        properties.getReminder().setThresholdHours(0);
        approvals.save(approval("two-hours", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(2 * 3600)));

        assertThat(properties.getReminder().resolvedThresholdHours()).isEqualTo(1);
        assertThat(useCase(NOW).remindDue()).isEqualTo(1);
    }

    @Test
    void nonAdminViewSeesOnlyOwnOverdueItems() {
        approvals.save(approval("a-old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600)));
        approvals.save(approval("a-fresh", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(3600)));
        approvals.save(approval("b-old", OTHER_USER_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600)));

        ApprovalReminderView view = useCase(NOW).view(OPERATOR_ID, 20);

        assertThat(view.thresholdHours()).isEqualTo(24);
        assertThat(view.items()).singleElement().satisfies(item -> {
            assertThat(item.approval().approvalId()).isEqualTo("a-old");
            assertThat(item.overdueHours()).isEqualTo(30);
        });
    }

    @Test
    void adminViewSeesAllOverdueItems() {
        approvals.save(approval("a-old", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600)));
        approvals.save(approval("b-old", OTHER_USER_ID, ApprovalStatus.PENDING, NOW.minusSeconds(40 * 3600)));

        ApprovalReminderView view = useCase(NOW).view(ADMIN_ID, 20);

        assertThat(view.items()).extracting(item -> item.approval().approvalId())
                .containsExactly("b-old", "a-old");
    }

    @Test
    void viewRespectsLimitAndClampsInvalidValues() {
        approvals.save(approval("a1", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600)));
        approvals.save(approval("a2", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(40 * 3600)));
        approvals.save(approval("a3", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(50 * 3600)));

        assertThat(useCase(NOW).view(OPERATOR_ID, 2).items()).hasSize(2);
        assertThat(useCase(NOW).view(OPERATOR_ID, -1).items()).hasSize(3);
    }

    @Test
    void singleFailureDoesNotStopTheRound() {
        PlatformApproval first = approval("fail", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600));
        PlatformApproval second = approval("ok", OPERATOR_ID, ApprovalStatus.PENDING, NOW.minusSeconds(30 * 3600));
        PlatformApprovalPort port = mock(PlatformApprovalPort.class);
        when(port.listRemindable(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(first, second));
        org.mockito.Mockito.doThrow(new IllegalStateException("db blip"))
                .when(port).markReminded(org.mockito.ArgumentMatchers.eq("fail"), org.mockito.ArgumentMatchers.any());
        ApprovalReminderUseCase useCase = new ApprovalReminderUseCase(
                port, checker, auditLog, observability, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        int reminded = useCase.remindDue();

        assertThat(reminded).isEqualTo(1);
        org.mockito.Mockito.verify(port).markReminded(
                org.mockito.ArgumentMatchers.eq("ok"), org.mockito.ArgumentMatchers.any());
    }

    private ApprovalReminderUseCase useCase(Instant now) {
        return new ApprovalReminderUseCase(
                approvals, checker, auditLog, observability, properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private PlatformApproval approval(
            String id,
            long userId,
            ApprovalStatus status,
            Instant createdAt
    ) {
        return new PlatformApproval(
                id, "e-" + id, "default", userId, "patient-analysis", "P002", "HIGH", "任务",
                status, "", null, createdAt, null);
    }
}
