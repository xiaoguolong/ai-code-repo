package com.aicode.framework.followup.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.followup.domain.model.FollowUpPriority;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.infrastructure.FollowUpAgent;
import com.aicode.framework.followup.infrastructure.persistence.InMemoryFollowUpTaskAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * 随访任务状态机（Week 24）：完成/取消、幂等 409、越权 403、不存在 404。
 */
class FollowUpTaskActionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long OTHER_ID = 3L;

    private InMemoryFollowUpTaskAdapter taskPort;
    private InMemoryAuditLogAdapter audit;
    private FollowUpTaskActionUseCase actionUseCase;
    private FollowUpTask pendingTask;

    @BeforeEach
    void setUp() {
        taskPort = new InMemoryFollowUpTaskAdapter();
        audit = new InMemoryAuditLogAdapter();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(OTHER_ID, "operator2", "hash", "operator"));
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);

        FollowUpAgent agent = mock(FollowUpAgent.class);
        FollowUpTaskUseCase useCase = new FollowUpTaskUseCase(
                agent, null, taskPort, users, checker, audit,
                new PlatformFollowUpProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        actionUseCase = new FollowUpTaskActionUseCase(
                useCase, taskPort, checker, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        pendingTask = new FollowUpTask(
                "t1", "default", OPERATOR_ID, "P002", FollowUpSource.AGENT, null, null,
                "复测血压", "3 天后复测", FollowUpPriority.HIGH, FollowUpStatus.PENDING,
                NOW.plusSeconds(86400L), NOW, NOW, null, "");
        taskPort.save(pendingTask);
    }

    @Test
    void completesPendingTaskAndWritesAudit() {
        FollowUpTask completed = actionUseCase.complete(OPERATOR_ID, "t1", "已复测 138/86");

        assertThat(completed.status()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(NOW);
        assertThat(completed.updatedAt()).isEqualTo(NOW);
        assertThat(completed.note()).isEqualTo("已复测 138/86");
        assertThat(audit.listAll()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.FOLLOWUP_TASK_COMPLETE);
            assertThat(entry.resource()).contains("t1");
        });
    }

    @Test
    void cancelsPendingTask() {
        FollowUpTask cancelled = actionUseCase.cancel(OPERATOR_ID, "t1", "患者已转诊");

        assertThat(cancelled.status()).isEqualTo(FollowUpStatus.CANCELLED);
        assertThat(cancelled.completedAt()).isNull();
        assertThat(audit.listAll()).anySatisfy(entry ->
                assertThat(entry.action()).isEqualTo(AuditAction.FOLLOWUP_TASK_CANCEL));
    }

    @Test
    void duplicateCompletionConflicts() {
        actionUseCase.complete(OPERATOR_ID, "t1", null);

        assertThatThrownBy(() -> actionUseCase.complete(OPERATOR_ID, "t1", "again"))
                .isInstanceOf(PlatformConflictException.class);
        assertThatThrownBy(() -> actionUseCase.cancel(OPERATOR_ID, "t1", "late"))
                .isInstanceOf(PlatformConflictException.class);
    }

    @Test
    void otherUserCannotTransitionTask() {
        assertThatThrownBy(() -> actionUseCase.complete(OTHER_ID, "t1", null))
                .isInstanceOf(PlatformAccessDeniedException.class);
        assertThat(taskPort.findById("t1").orElseThrow().status())
                .isEqualTo(FollowUpStatus.PENDING);
    }

    @Test
    void adminCanTransitionAnotherUsersTask() {
        FollowUpTask completed = actionUseCase.complete(ADMIN_ID, "t1", "管理员代确认");

        assertThat(completed.status()).isEqualTo(FollowUpStatus.COMPLETED);
    }

    @Test
    void unknownTaskIsNotFound() {
        assertThatThrownBy(() -> actionUseCase.complete(OPERATOR_ID, "missing", null))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void noteTooLongIsRejected() {
        assertThatThrownBy(() -> actionUseCase.complete(OPERATOR_ID, "t1", "x".repeat(501)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void blankTaskIdIsRejected() {
        assertThatThrownBy(() -> actionUseCase.complete(OPERATOR_ID, "  ", null))
                .isInstanceOf(InvalidChatRequestException.class);
    }
}
