package com.aicode.framework.followup.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpPriority;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.infrastructure.FollowUpAgent;
import com.aicode.framework.followup.infrastructure.persistence.InMemoryFollowUpTaskAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 随访任务用例（Week 24）：落库、租户/用户隔离、审批幂等、站内提醒分组。
 */
class FollowUpTaskUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long OTHER_ID = 3L;

    private InMemoryFollowUpTaskAdapter taskPort;
    private InMemoryPlatformUserAdapter users;
    private PlatformFollowUpProperties properties;
    private FollowUpTaskUseCase useCase;

    @BeforeEach
    void setUp() {
        taskPort = new InMemoryFollowUpTaskAdapter();
        users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(OTHER_ID, "operator2", "hash", "operator"));
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));

        FollowUpAgent agent = mock(FollowUpAgent.class);
        when(agent.run(eq("P002"), any())).thenReturn(highRiskPlan());
        properties = new PlatformFollowUpProperties();
        useCase = new FollowUpTaskUseCase(
                agent, new FollowUpPlanner(), taskPort, users,
                new PlatformPermissionChecker(users, roles), new InMemoryAuditLogAdapter(),
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runPersistsRuleTasksInTheCallerScope() {
        FollowUpRunResult result = useCase.run(
                "P002", null, PlatformMemoryScope.of(users.findById(OPERATOR_ID).orElseThrow()));

        assertThat(result.tasks()).hasSize(3);
        assertThat(result.tasks()).allSatisfy(task -> {
            assertThat(task.userId()).isEqualTo(OPERATOR_ID);
            assertThat(task.tenantKey()).isEqualTo("default");
            assertThat(task.source()).isEqualTo(FollowUpSource.AGENT);
            assertThat(task.status()).isEqualTo(FollowUpStatus.PENDING);
        });
        assertThat(taskPort.size()).isEqualTo(3);
    }

    @Test
    void runWithoutScopeReturnsPlanButPersistsNothing() {
        FollowUpRunResult result = useCase.run("P002", null, null);

        assertThat(result.plan().tasks()).hasSize(3);
        assertThat(result.tasks()).isEmpty();
        assertThat(taskPort.size()).isZero();
    }

    @Test
    void runRejectsBlankPatientId() {
        assertThatThrownBy(() -> useCase.run("  ", null, null))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void listIsScopedToTenantAndUserWithAdminOverride() {
        useCase.run("P002", null, PlatformMemoryScope.of(users.findById(OPERATOR_ID).orElseThrow()));

        assertThat(useCase.list(OPERATOR_ID, null, FollowUpStatus.PENDING, null, 20)).hasSize(3);
        assertThat(useCase.list(OTHER_ID, null, FollowUpStatus.PENDING, null, 20)).isEmpty();
        assertThat(useCase.list(ADMIN_ID, OPERATOR_ID, FollowUpStatus.PENDING, null, 20)).hasSize(3);
        assertThatThrownBy(() ->
                useCase.list(OPERATOR_ID, OTHER_ID, FollowUpStatus.PENDING, null, 20))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void listFiltersByPatientAndStatus() {
        useCase.run("P002", null, PlatformMemoryScope.of(users.findById(OPERATOR_ID).orElseThrow()));

        assertThat(useCase.list(OPERATOR_ID, null, FollowUpStatus.PENDING, "P002", 20)).hasSize(3);
        assertThat(useCase.list(OPERATOR_ID, null, FollowUpStatus.PENDING, "P999", 20)).isEmpty();
        assertThat(useCase.list(OPERATOR_ID, null, FollowUpStatus.COMPLETED, null, 20)).isEmpty();
    }

    @Test
    void getRejectsOtherUsersTask() {
        FollowUpTask task = useCase.run(
                "P002", null, PlatformMemoryScope.of(users.findById(OPERATOR_ID).orElseThrow()))
                .tasks().get(0);

        assertThat(useCase.get(OPERATOR_ID, task.taskId()).taskId()).isEqualTo(task.taskId());
        assertThat(useCase.get(ADMIN_ID, task.taskId()).taskId()).isEqualTo(task.taskId());
        assertThatThrownBy(() -> useCase.get(OTHER_ID, task.taskId()))
                .isInstanceOf(PlatformAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.get(OPERATOR_ID, "missing"))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void createFromApprovalIsIdempotentPerTitle() {
        PlatformApproval approval = approval("a1", "P002", "HIGH");

        List<String> first = useCase.createFromApproval(approval, NOW);
        List<String> second = useCase.createFromApproval(approval, NOW);

        assertThat(first).hasSize(3);
        assertThat(second).isEmpty();
        assertThat(taskPort.listByApproval("a1")).hasSize(3);
        assertThat(taskPort.listByApproval("a1")).allSatisfy(task -> {
            assertThat(task.source()).isEqualTo(FollowUpSource.APPROVAL);
            assertThat(task.approvalId()).isEqualTo("a1");
            assertThat(task.userId()).isEqualTo(OPERATOR_ID);
        });
    }

    @Test
    void createFromApprovalUsesRiskLevelForTaskCount() {
        assertThat(useCase.createFromApproval(approval("a2", "P003", "MEDIUM"), NOW)).hasSize(2);
        assertThat(useCase.createFromApproval(approval("a3", "P004", "LOW"), NOW)).hasSize(1);
    }

    @Test
    void remindersSplitOverdueAndDueSoonAndOnlyReturnPending() {
        PlatformMemoryScope scope = PlatformMemoryScope.of(users.findById(OPERATOR_ID).orElseThrow());
        taskPort.save(task("past", scope, NOW.minusSeconds(2 * 86400L), FollowUpStatus.PENDING));
        taskPort.save(task("soon", scope, NOW.plusSeconds(2 * 86400L), FollowUpStatus.PENDING));
        taskPort.save(task("far", scope, NOW.plusSeconds(30 * 86400L), FollowUpStatus.PENDING));
        taskPort.save(task("done", scope, NOW.minusSeconds(86400L), FollowUpStatus.COMPLETED));

        FollowUpReminderView view = useCase.reminders(OPERATOR_ID, 7);

        assertThat(view.days()).isEqualTo(7);
        assertThat(view.overdue()).extracting(FollowUpTask::taskId).containsExactly("past");
        assertThat(view.dueSoon()).extracting(FollowUpTask::taskId).containsExactly("soon");
        assertThat(view.total()).isEqualTo(2);
    }

    @Test
    void reminderDaysDefaultAndValidateRange() {
        assertThat(useCase.reminders(OPERATOR_ID, 0).days())
                .isEqualTo(properties.resolvedDefaultReminderDays());
        assertThatThrownBy(() -> useCase.reminders(OPERATOR_ID, 31))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    private FollowUpPlanResult highRiskPlan() {
        Instant base = NOW;
        List<FollowUpPlanItem> items = List.of(
                new FollowUpPlanItem("复测血压与症状", "3 天复测", FollowUpPriority.HIGH,
                        base.plusSeconds(3 * 86400L)),
                new FollowUpPlanItem("复查 HbA1c/空腹血糖", "14 天复查", FollowUpPriority.HIGH,
                        base.plusSeconds(14 * 86400L)),
                new FollowUpPlanItem("门诊复诊", "30 天复诊", FollowUpPriority.HIGH,
                        base.plusSeconds(30 * 86400L)));
        return new FollowUpPlanResult(
                "P002", "HIGH", "计划文本", items, false, TokenUsage.unknown(), "test-model", List.of());
    }

    private PlatformApproval approval(String approvalId, String patientId, String riskLevel) {
        return new PlatformApproval(
                approvalId, "exec-" + approvalId, "default", OPERATOR_ID, "patient-analysis",
                patientId, riskLevel, "为患者生成分析报告", ApprovalStatus.APPROVED, "",
                ADMIN_ID, NOW.minusSeconds(60), NOW);
    }

    private FollowUpTask task(
            String taskId, PlatformMemoryScope scope, Instant dueAt, FollowUpStatus status) {
        return new FollowUpTask(
                taskId, scope.tenantKey(), scope.userId(), "P002", FollowUpSource.AGENT,
                null, null, "任务 " + taskId, "", FollowUpPriority.HIGH, status,
                dueAt, NOW, NOW, status == FollowUpStatus.COMPLETED ? NOW : null, "");
    }
}
