package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.core.domain.model.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 审批决策事务（Week 24，TD-02）：执行记录终态写失败时，整个决策回滚。
 *
 * <p>这是 {@code @Transactional} 的直接证据：{@code markDecided} 已把审批改成 APPROVED，
 * 但最后一步 {@code saveTerminal} 抛异常 → 事务回滚 → 审批仍是 PENDING、随访任务也不留下。
 * 只有 JDBC 模式 + 真实事务管理器才能验证，所以这里用 H2 + 真实适配器。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "platform.approval.pending-gauge.enabled=false",
        "platform.approval.reconciliation.enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformApprovalTransactionTest {

    private static final long USER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");

    @Autowired
    private PlatformApprovalPort approvalPort;

    @Autowired
    private ExecutionRecordPort executionRecordPort;

    @Autowired
    private PlatformApprovalUseCase approvalUseCase;

    @Autowired
    private PlatformApprovalDecisionUseCase decisionUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PlatformExecutionRecordWriter executionRecordWriter;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM platform_followup_task WHERE patient_id = ?", "P009");
        jdbcTemplate.update("DELETE FROM platform_approval WHERE patient_id = ?", "P009");
        jdbcTemplate.update("DELETE FROM execution_record WHERE execution_id LIKE 'w24-tx-%'");
    }

    @Test
    void failingTerminalWriteRollsBackApprovalAndFollowUpTasks() {
        doThrow(new IllegalStateException("execution db down"))
                .when(executionRecordWriter).saveTerminal(any(), any(), any(), any());

        PlatformApproval approval = approvalUseCase.create(
                "w24-tx-1", USER_ID, "patient-analysis", "P009", "HIGH", "事务回滚验证");
        executionRecordPort.save(new ExecutionRecord(
                "w24-tx-1", USER_ID, "patient-analysis", AgentType.PATIENT_ANALYSIS,
                ExecutionStatus.PENDING_APPROVAL, "{\"patientId\":\"P009\"}",
                "{\"patientId\":\"P009\",\"riskLevel\":\"HIGH\",\"report\":\"报告\"}",
                "deepseek-chat", new TokenUsage(1, 1, 2), "", NOW, null));

        assertThatThrownBy(() -> decisionUseCase.decide(USER_ID, approval.approvalId(), true, "同意"))
                .isInstanceOf(IllegalStateException.class);

        PlatformApproval reloaded = approvalPort.findById(approval.approvalId()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(reloaded.decidedAt()).isNull();
        assertThat(executionRecordPort.findById("w24-tx-1").orElseThrow().status())
                .isEqualTo(ExecutionStatus.PENDING_APPROVAL);
        Integer tasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_followup_task WHERE approval_id = ?",
                Integer.class, approval.approvalId());
        assertThat(tasks).isZero();
    }
}
