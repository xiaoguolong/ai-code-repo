package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryExecutionRecordAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformApprovalAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批/执行一致性对账（Week 24，TD-02）：历史脏数据按审批终态补齐执行状态。
 */
class ApprovalReconciliationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemoryPlatformApprovalAdapter approvals;
    private InMemoryExecutionRecordAdapter executions;
    private ApprovalReconciliationUseCase useCase;

    @BeforeEach
    void setUp() {
        approvals = new InMemoryPlatformApprovalAdapter();
        executions = new InMemoryExecutionRecordAdapter();
        useCase = new ApprovalReconciliationUseCase(
                approvals, executions, new PlatformExecutionRecordWriter(executions, MAPPER),
                MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approvedButStillPendingExecutionIsCompleted() {
        saveApproval("a1", "e1", ApprovalStatus.APPROVED);
        saveExecution("e1", ExecutionStatus.PENDING_APPROVAL);

        int fixed = useCase.reconcile(200);

        assertThat(fixed).isEqualTo(1);
        ExecutionRecord record = executions.findById("e1").orElseThrow();
        assertThat(record.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(record.finishedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(record.outputJson())
                .contains("\"approvalStatus\":\"APPROVED\"")
                .contains("\"reconciled\":true");
    }

    @Test
    void rejectedButStillPendingExecutionIsRejected() {
        saveApproval("a2", "e2", ApprovalStatus.REJECTED);
        saveExecution("e2", ExecutionStatus.PENDING_APPROVAL);

        assertThat(useCase.reconcile(200)).isEqualTo(1);
        assertThat(executions.findById("e2").orElseThrow().status())
                .isEqualTo(ExecutionStatus.REJECTED);
    }

    @Test
    void pendingApprovalsAndTerminalExecutionsAreUntouched() {
        saveApproval("a3", "e3", ApprovalStatus.PENDING);
        saveExecution("e3", ExecutionStatus.PENDING_APPROVAL);
        saveApproval("a4", "e4", ApprovalStatus.APPROVED);
        saveExecution("e4", ExecutionStatus.COMPLETED);

        assertThat(useCase.reconcile(200)).isZero();
        assertThat(executions.findById("e3").orElseThrow().status())
                .isEqualTo(ExecutionStatus.PENDING_APPROVAL);
        assertThat(executions.findById("e4").orElseThrow().status())
                .isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void missingExecutionIsSkippedWithoutFailing() {
        saveApproval("a5", "missing", ApprovalStatus.APPROVED);

        assertThat(useCase.reconcile(200)).isZero();
    }

    @Test
    void reconciliationIsIdempotent() {
        saveApproval("a6", "e6", ApprovalStatus.APPROVED);
        saveExecution("e6", ExecutionStatus.PENDING_APPROVAL);

        assertThat(useCase.reconcile(200)).isEqualTo(1);
        assertThat(useCase.reconcile(200)).isZero();
    }

    @Test
    void limitZeroIsClampedToOne() {
        saveApproval("a7", "e7", ApprovalStatus.APPROVED);
        saveApproval("a8", "e8", ApprovalStatus.APPROVED);
        saveExecution("e7", ExecutionStatus.PENDING_APPROVAL);
        saveExecution("e8", ExecutionStatus.PENDING_APPROVAL);

        assertThat(useCase.reconcile(0)).isEqualTo(1);
    }

    private void saveApproval(String approvalId, String executionId, ApprovalStatus status) {
        approvals.save(new PlatformApproval(
                approvalId, executionId, "default", 2L, "patient-analysis", "P002", "HIGH", "任务",
                status, "", status == ApprovalStatus.PENDING ? null : 1L,
                NOW.minusSeconds(120), status == ApprovalStatus.PENDING ? null : NOW.minusSeconds(60)));
    }

    private void saveExecution(String executionId, ExecutionStatus status) {
        executions.save(new ExecutionRecord(
                executionId, 2L, "patient-analysis", AgentType.PATIENT_ANALYSIS, status,
                "{}", "{\"patientId\":\"P002\",\"riskLevel\":\"HIGH\"}", "deepseek-chat",
                new TokenUsage(1, 1, 2), "", NOW.minusSeconds(300), null));
    }
}
