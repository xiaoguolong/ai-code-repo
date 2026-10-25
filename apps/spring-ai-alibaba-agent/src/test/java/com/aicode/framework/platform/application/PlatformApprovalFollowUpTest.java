package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import com.aicode.framework.followup.application.FollowUpTaskUseCase;
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.followup.infrastructure.persistence.InMemoryFollowUpTaskAdapter;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformFollowUpProperties;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审批通过 → 随访任务（Week 24）：批准生成 3 条 HIGH 任务、拒绝不生成、任务写失败不影响审批。
 */
class PlatformApprovalFollowUpTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryPlatformApprovalAdapter approvals;
    private InMemoryExecutionRecordAdapter executions;
    private InMemoryFollowUpTaskAdapter taskPort;
    private InMemoryPlatformUserAdapter users;
    private PlatformApprovalUseCase approvalUseCase;
    private PlatformApprovalDecisionUseCase decisionUseCase;
    private FollowUpTaskUseCase followUpTaskUseCase;

    @BeforeEach
    void setUp() {
        approvals = new InMemoryPlatformApprovalAdapter();
        executions = new InMemoryExecutionRecordAdapter();
        taskPort = new InMemoryFollowUpTaskAdapter();
        users = new InMemoryPlatformUserAdapter();
        InMemoryAuditLogAdapter audit = new InMemoryAuditLogAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        PlatformPermissionChecker checker = new PlatformPermissionChecker(users, roles);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HashingEmbeddingAdapter embedder = new HashingEmbeddingAdapter(
                new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10));
        EmbeddingProperties embeddingProperties = new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10);
        PlatformMemoryUseCase memoryUseCase = new PlatformMemoryUseCase(
                new InMemoryPlatformMemoryAdapter(clock), users, checker, embedder,
                new PlatformMemoryProperties(500, 0.05, 5, 20),
                new PlatformMemoryLifecycleProperties(30, false, "", 100), clock,
                new MemoryEmbeddingIdentity(embeddingProperties));
        followUpTaskUseCase = new FollowUpTaskUseCase(
                null, new FollowUpPlanner(), taskPort, users, checker, audit,
                new PlatformFollowUpProperties(), clock);
        approvalUseCase = new PlatformApprovalUseCase(approvals, users, checker, audit, clock);
        decisionUseCase = new PlatformApprovalDecisionUseCase(
                approvals, executions, users, checker, audit, MAPPER,
                new PlatformExecutionRecordWriter(executions, MAPPER), memoryUseCase,
                followUpTaskUseCase, clock);
    }

    @Test
    void approvingHighRiskCreatesThreeFollowUpTasks() {
        PlatformApproval approval = pendingApproval("exec-1");
        saveExecution("exec-1", ExecutionStatus.PENDING_APPROVAL);

        ApprovalDecision decision = decisionUseCase.decide(2L, approval.approvalId(), true, "同意进入随访");

        assertThat(decision.memoryId()).isNotBlank();
        assertThat(decision.followUpTaskIds()).hasSize(3);
        List<FollowUpTask> tasks = taskPort.listByApproval(approval.approvalId());
        assertThat(tasks).hasSize(3);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.source()).isEqualTo(FollowUpSource.APPROVAL);
            assertThat(task.status()).isEqualTo(FollowUpStatus.PENDING);
            assertThat(task.userId()).isEqualTo(2L);
            assertThat(task.patientId()).isEqualTo("P002");
        });
        assertThat(taskPort.list("default", 2L, FollowUpStatus.PENDING, "P002", 10)).hasSize(3);
        ExecutionRecord completed = executions.findById("exec-1").orElseThrow();
        assertThat(completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(completed.outputJson()).contains("\"followUpTaskIds\"");
    }

    @Test
    void rejectingHighRiskCreatesNoFollowUpTask() {
        PlatformApproval approval = pendingApproval("exec-2");
        saveExecution("exec-2", ExecutionStatus.PENDING_APPROVAL);

        ApprovalDecision decision = decisionUseCase.decide(2L, approval.approvalId(), false, "指标需复测");

        assertThat(decision.followUpTaskIds()).isEmpty();
        assertThat(taskPort.listByApproval(approval.approvalId())).isEmpty();
        assertThat(executions.findById("exec-2").orElseThrow().status())
                .isEqualTo(ExecutionStatus.REJECTED);
    }

    @Test
    void taskPersistenceFailureDoesNotBreakApproval() {
        FollowUpTaskUseCase failingTasks = new FollowUpTaskUseCase(
                null, new FollowUpPlanner(), new FailingFollowUpTaskPort(taskPort), users,
                new PlatformPermissionChecker(users, roles()), new InMemoryAuditLogAdapter(),
                new PlatformFollowUpProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        PlatformApprovalDecisionUseCase decisionWithFailure = new PlatformApprovalDecisionUseCase(
                approvals, executions, users, new PlatformPermissionChecker(users, roles()),
                new InMemoryAuditLogAdapter(), MAPPER,
                new PlatformExecutionRecordWriter(executions, MAPPER),
                null, failingTasks, Clock.fixed(NOW, ZoneOffset.UTC));
        PlatformApproval approval = pendingApproval("exec-3");
        saveExecution("exec-3", ExecutionStatus.PENDING_APPROVAL);

        ApprovalDecision decision = decisionWithFailure.decide(2L, approval.approvalId(), true, "同意");

        assertThat(decision.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(decision.followUpTaskIds()).isEmpty();
        assertThat(executions.findById("exec-3").orElseThrow().status())
                .isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void duplicateApprovalDoesNotCreateASecondSetOfTasks() {
        PlatformApproval approval = pendingApproval("exec-4");
        saveExecution("exec-4", ExecutionStatus.PENDING_APPROVAL);
        decisionUseCase.decide(2L, approval.approvalId(), true, "同意");

        assertThatThrownBy(() -> decisionUseCase.decide(2L, approval.approvalId(), true, "再批一次"))
                .isInstanceOf(com.aicode.framework.platform.domain.exception.PlatformConflictException.class);
        assertThat(taskPort.listByApproval(approval.approvalId())).hasSize(3);
    }

    private InMemoryPlatformRoleAdapter roles() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        return roles;
    }

    private PlatformApproval pendingApproval(String executionId) {
        return approvalUseCase.create(
                executionId, 2L, "patient-analysis", "P002", "HIGH", "为患者 P002 生成分析报告");
    }

    private void saveExecution(String executionId, ExecutionStatus status) {
        executions.save(new ExecutionRecord(
                executionId, 2L, "patient-analysis", AgentType.PATIENT_ANALYSIS, status,
                "{\"patientId\":\"P002\"}",
                "{\"patientId\":\"P002\",\"riskLevel\":\"HIGH\",\"report\":\"报告正文\","
                        + "\"task\":\"为患者 P002 生成分析报告\"}",
                "deepseek-chat", new TokenUsage(10, 20, 30), "", NOW, null));
    }

    /**
     * 保存必失败的随访端口：委托读操作，写操作抛异常（模拟唯一索引/数据库故障）。
     */
    private static final class FailingFollowUpTaskPort implements FollowUpTaskPort {

        private final FollowUpTaskPort delegate;

        private FailingFollowUpTaskPort(FollowUpTaskPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(FollowUpTask task) {
            throw new IllegalStateException("db down");
        }

        @Override
        public Optional<FollowUpTask> findById(String taskId) {
            return delegate.findById(taskId);
        }

        @Override
        public List<FollowUpTask> list(
                String tenantKey, long userId, FollowUpStatus status, String patientId, int limit) {
            return delegate.list(tenantKey, userId, status, patientId, limit);
        }

        @Override
        public List<FollowUpTask> listPendingDue(
                String tenantKey, long userId, Instant fromInclusive, Instant toExclusive, int limit) {
            return delegate.listPendingDue(tenantKey, userId, fromInclusive, toExclusive, limit);
        }

        @Override
        public List<FollowUpTask> listByApproval(String approvalId) {
            return delegate.listByApproval(approvalId);
        }

        @Override
        public boolean markStatus(
                String taskId, String tenantKey, long userId, FollowUpStatus target,
                String note, Instant updatedAt, Instant completedAt) {
            return delegate.markStatus(taskId, tenantKey, userId, target, note, updatedAt, completedAt);
        }

        @Override
        public long countByStatus(String tenantKey, long userId, FollowUpStatus status) {
            return delegate.countByStatus(tenantKey, userId, status);
        }
    }
}
