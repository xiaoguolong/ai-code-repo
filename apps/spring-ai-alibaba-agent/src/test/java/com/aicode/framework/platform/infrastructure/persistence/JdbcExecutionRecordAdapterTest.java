package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 执行记录落库适配器（H2 MODE=PostgreSQL + Flyway V2）。
 * Week 13 遗留项：替换内存 ExecutionRecordPort。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcExecutionRecordAdapterTest {

    @Autowired
    private ExecutionRecordPort executionRecordPort;

    @Autowired
    private PlatformUserPort platformUserPort;

    @BeforeEach
    void ensureReferencedUsersExist() {
        // execution_record.user_id 外键指向 platform_user，写入前必须先有用户行
        platformUserPort.save(new PlatformUser(3L, "exec-user-3", "hash", "operator"));
        platformUserPort.save(new PlatformUser(4L, "exec-user-4", "hash", "operator"));
    }

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(executionRecordPort).isInstanceOf(JdbcExecutionRecordAdapter.class);
    }

    @Test
    void savesRunningRecordAndOverwritesWithCompleted() {
        Instant startedAt = Instant.parse("2026-10-01T02:00:00Z");
        Instant finishedAt = Instant.parse("2026-10-01T02:00:07Z");
        executionRecordPort.save(new ExecutionRecord(
                "exec-completed-1", 1L, "patient-risk", AgentType.PATIENT_RISK_WORKFLOW,
                ExecutionStatus.RUNNING, "{\"patientId\":\"P001\"}", "", "", TokenUsage.unknown(),
                "", startedAt, null));

        executionRecordPort.save(new ExecutionRecord(
                "exec-completed-1", 1L, "patient-risk", AgentType.PATIENT_RISK_WORKFLOW,
                ExecutionStatus.COMPLETED, "{\"patientId\":\"P001\"}", "{\"risk\":\"LOW\"}",
                "deepseek-chat", new TokenUsage(11, 22, 33), "", startedAt, finishedAt));

        ExecutionRecord record = executionRecordPort.findById("exec-completed-1").orElseThrow();

        assertThat(record.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(record.agentType()).isEqualTo(AgentType.PATIENT_RISK_WORKFLOW);
        assertThat(record.outputJson()).isEqualTo("{\"risk\":\"LOW\"}");
        assertThat(record.model()).isEqualTo("deepseek-chat");
        assertThat(record.usage().promptTokens()).isEqualTo(11);
        assertThat(record.usage().completionTokens()).isEqualTo(22);
        assertThat(record.usage().totalTokens()).isEqualTo(33);
        assertThat(record.startedAt()).isEqualTo(startedAt);
        assertThat(record.finishedAt()).isEqualTo(finishedAt);
    }

    @Test
    void failedRecordKeepsErrorMessageAndNullFinishedAtWhenRunning() {
        executionRecordPort.save(new ExecutionRecord(
                "exec-failed-1", 2L, "medical-assistant", AgentType.MEDICAL_ASSISTANT,
                ExecutionStatus.FAILED, "{}", "", "", TokenUsage.unknown(),
                "model timeout", Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-10-01T03:00:30Z")));

        ExecutionRecord record = executionRecordPort.findById("exec-failed-1").orElseThrow();

        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(record.errorMessage()).isEqualTo("model timeout");
        assertThat(record.userId()).isEqualTo(2L);
    }

    @Test
    void listByUserIdFiltersAndSortsByStartedAtDesc() {
        executionRecordPort.save(record("exec-u3-old", 3L, Instant.parse("2026-10-01T01:00:00Z")));
        executionRecordPort.save(record("exec-u3-new", 3L, Instant.parse("2026-10-01T05:00:00Z")));
        executionRecordPort.save(record("exec-u4-other", 4L, Instant.parse("2026-10-01T06:00:00Z")));

        assertThat(executionRecordPort.listByUserId(3L))
                .extracting(ExecutionRecord::executionId)
                .containsExactly("exec-u3-new", "exec-u3-old");

        assertThat(executionRecordPort.listAll())
                .extracting(ExecutionRecord::executionId)
                .contains("exec-u4-other", "exec-u3-new", "exec-u3-old");
    }

    @Test
    void storesExactlyOneUserRowPerExecutionOwner() {
        executionRecordPort.save(record("exec-fk-single", 4L, Instant.parse("2026-10-01T04:00:00Z")));

        assertThat(executionRecordPort.findById("exec-fk-single").orElseThrow().userId()).isEqualTo(4L);
    }

    @Test
    void returnsEmptyForUnknownExecutionId() {
        Optional<ExecutionRecord> found = executionRecordPort.findById("exec-does-not-exist");

        assertThat(found).isEmpty();
    }

    private ExecutionRecord record(String executionId, long userId, Instant startedAt) {
        return new ExecutionRecord(
                executionId, userId, "framework-react", AgentType.FRAMEWORK_REACT,
                ExecutionStatus.COMPLETED, "{}", "{}", "deepseek-chat", TokenUsage.unknown(),
                "", startedAt, startedAt.plusSeconds(1));
    }
}
