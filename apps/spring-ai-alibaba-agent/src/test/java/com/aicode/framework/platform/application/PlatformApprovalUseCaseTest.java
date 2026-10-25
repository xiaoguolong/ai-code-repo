package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformMemoryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人工审批用例（Week 23）：创建待办、批准写记忆、拒绝不写、权限/幂等/失败降级。
 */
class PlatformApprovalUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryPlatformApprovalAdapter approvals;
    private InMemoryExecutionRecordAdapter executions;
    private InMemoryPlatformUserAdapter users;
    private InMemoryAuditLogAdapter audit;
    private PlatformPermissionChecker checker;
    private PlatformMemoryUseCase memoryUseCase;
    private PlatformApprovalUseCase useCase;
    private PlatformApprovalDecisionUseCase decisionUseCase;

    @BeforeEach
    void setUp() {
        approvals = new InMemoryPlatformApprovalAdapter();
        executions = new InMemoryExecutionRecordAdapter();
        users = new InMemoryPlatformUserAdapter();
        audit = new InMemoryAuditLogAdapter();

        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "operator2", "hash", "operator"));
        users.save(new PlatformUser(4L, "operator3", "hash", "operator"));
        checker = new PlatformPermissionChecker(users, roles);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HashingEmbeddingAdapter embedder = new HashingEmbeddingAdapter(
                new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10));
        memoryUseCase = new PlatformMemoryUseCase(
                new InMemoryPlatformMemoryAdapter(clock), users, checker, embedder,
                new PlatformMemoryProperties(500, 0.05, 5, 20),
                new PlatformMemoryLifecycleProperties(30, false, "", 100),
                clock,
                new MemoryEmbeddingIdentity(new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10)));
        useCase = new PlatformApprovalUseCase(approvals, users, checker, audit, clock);
        decisionUseCase = new PlatformApprovalDecisionUseCase(
                approvals, executions, memoryUseCase, users, checker, audit, MAPPER, clock,
                new PlatformExecutionRecordWriter(executions, MAPPER));
    }

    @Test
    void createsPendingApprovalWithOwnerTenantAndTimestamp() {
        PlatformApproval approval = useCase.create(
                "exec-1", 2L, "patient-analysis", "P002", "HIGH", "为患者 P002 生成分析报告");

        assertThat(approval.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.tenantKey()).isEqualTo("default");
        assertThat(approval.userId()).isEqualTo(2L);
        assertThat(approval.createdAt()).isEqualTo(NOW);
        assertThat(approval.decidedAt()).isNull();
        assertThat(approvals.findById(approval.approvalId())).isPresent();
        assertThat(audit.listAll())
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo(AuditAction.APPROVAL_SUBMIT);
                    assertThat(entry.result()).isEqualTo(AuditResult.SUCCESS);
                });
    }

    @Test
    void approveWritesMemoryAndCompletesExecution() {
        PlatformApproval approval = pendingApproval("exec-1", 2L);
        saveExecution("exec-1", 2L);

        ApprovalDecision decision =
                decisionUseCase.decide(2L, approval.approvalId(), true, "同意进入随访");

        assertThat(decision.approval().status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decision.approval().decidedBy()).isEqualTo(2L);
        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(decision.memoryId()).isNotBlank();

        ExecutionRecord completed = executions.findById("exec-1").orElseThrow();
        assertThat(completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(completed.finishedAt()).isEqualTo(NOW);
        assertThat(completed.outputJson())
                .contains("\"approvalStatus\":\"APPROVED\"")
                .contains(decision.memoryId());

        assertThat(memoryUseCase.list(2L, null, "P002", null, 5))
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.record().task()).isEqualTo("为患者 P002 生成分析报告");
                    assertThat(hit.record().answer()).isEqualTo("报告正文");
                });
        assertThat(audit.listAll())
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo(AuditAction.APPROVAL_DECIDE);
                    assertThat(entry.result()).isEqualTo(AuditResult.SUCCESS);
                });
    }

    @Test
    void rejectDoesNotWriteMemoryAndMarksExecutionRejected() {
        PlatformApproval approval = pendingApproval("exec-2", 2L);
        saveExecution("exec-2", 2L);

        ApprovalDecision decision =
                decisionUseCase.decide(2L, approval.approvalId(), false, "指标需复测");

        assertThat(decision.approval().status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(decision.memoryId()).isNull();

        ExecutionRecord rejected = executions.findById("exec-2").orElseThrow();
        assertThat(rejected.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(rejected.outputJson()).contains("\"approvalStatus\":\"REJECTED\"");
        assertThat(memoryUseCase.list(2L, null, "P002", null, 5)).isEmpty();
        assertThat(audit.listAll())
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo(AuditAction.APPROVAL_DECIDE);
                    assertThat(entry.result()).isEqualTo(AuditResult.DENIED);
                });
    }

    @Test
    void nonOwnerCannotDecide() {
        PlatformApproval approval = pendingApproval("exec-3", 2L);
        saveExecution("exec-3", 2L);

        assertThatThrownBy(() -> decisionUseCase.decide(3L, approval.approvalId(), true, null))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void adminCanDecideAnotherUsersApproval() {
        PlatformApproval approval = pendingApproval("exec-4", 2L);
        saveExecution("exec-4", 2L);

        ApprovalDecision decision =
                decisionUseCase.decide(1L, approval.approvalId(), true, "管理员代审");

        assertThat(decision.approval().status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decision.approval().decidedBy()).isEqualTo(1L);
    }

    @Test
    void duplicateDecisionConflictsAndDoesNotWriteTwice() {
        PlatformApproval approval = pendingApproval("exec-5", 2L);
        saveExecution("exec-5", 2L);
        decisionUseCase.decide(2L, approval.approvalId(), true, "同意");

        assertThatThrownBy(() -> decisionUseCase.decide(2L, approval.approvalId(), false, "再拒一次"))
                .isInstanceOf(PlatformConflictException.class);

        long memories = memoryUseCase.list(2L, null, "P002", null, 10).size();
        assertThat(memories).isEqualTo(1);
    }

    @Test
    void listReturnsOnlyOwnForOperatorAndAllForAdmin() {
        pendingApproval("exec-6", 2L);
        pendingApproval("exec-7", 3L);

        assertThat(useCase.list(2L, ApprovalStatus.PENDING, 20))
                .extracting(PlatformApproval::userId)
                .containsExactly(2L);
        assertThat(useCase.list(1L, ApprovalStatus.PENDING, 20)).hasSize(2);
    }

    @Test
    void getRejectsNonOwner() {
        PlatformApproval approval = pendingApproval("exec-8", 2L);

        assertThatThrownBy(() -> useCase.get(3L, approval.approvalId()))
                .isInstanceOf(PlatformAccessDeniedException.class);
        assertThat(useCase.get(2L, approval.approvalId()).approvalId())
                .isEqualTo(approval.approvalId());
    }

    @Test
    void unknownApprovalIsNotFound() {
        assertThatThrownBy(() -> decisionUseCase.decide(2L, "missing", true, null))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void commentTooLongIsRejected() {
        PlatformApproval approval = pendingApproval("exec-9", 2L);
        saveExecution("exec-9", 2L);

        assertThatThrownBy(() -> decisionUseCase.decide(2L, approval.approvalId(), true, "x".repeat(501)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void memoryFailureStillCompletesDecision() {
        PlatformMemoryUseCase failingMemory = mock(PlatformMemoryUseCase.class);
        when(failingMemory.save(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("db down"));
        PlatformApprovalUseCase failing = new PlatformApprovalUseCase(
                approvals, users, checker, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        PlatformApprovalDecisionUseCase failingDecision = new PlatformApprovalDecisionUseCase(
                approvals, executions, failingMemory, users, checker, audit, MAPPER,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PlatformExecutionRecordWriter(executions, MAPPER));
        PlatformApproval approval = failing.create(
                "exec-10", 2L, "patient-analysis", "P002", "HIGH", "任务");
        saveExecution("exec-10", 2L);

        ApprovalDecision decision =
                failingDecision.decide(2L, approval.approvalId(), true, "同意");

        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(decision.memoryId()).isNull();
        assertThat(executions.findById("exec-10").orElseThrow().status())
                .isEqualTo(ExecutionStatus.COMPLETED);
    }

    private PlatformApproval pendingApproval(String executionId, long userId) {
        return useCase.create(
                executionId, userId, "patient-analysis", "P002", "HIGH", "为患者 P002 生成分析报告");
    }

    private void saveExecution(String executionId, long userId) {
        executions.save(new ExecutionRecord(
                executionId, userId, "patient-analysis", AgentType.PATIENT_ANALYSIS,
                ExecutionStatus.PENDING_APPROVAL,
                "{\"patientId\":\"P002\"}",
                "{\"patientId\":\"P002\",\"riskLevel\":\"HIGH\",\"report\":\"报告正文\","
                        + "\"task\":\"为患者 P002 生成分析报告\"}",
                "deepseek-chat", new TokenUsage(10, 20, 30), "", NOW, null));
    }
}
