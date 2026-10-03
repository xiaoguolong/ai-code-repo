package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计日志落库适配器（H2 MODE=PostgreSQL + Flyway V3）。
 * 审计只记标识与结果，不记密码 / token / API Key。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcAuditLogAdapterTest {

    @Autowired
    private AuditLogPort auditLogPort;

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(auditLogPort).isInstanceOf(JdbcAuditLogAdapter.class);
    }

    @Test
    void persistsDeniedAuditEntryAndReadsItBack() {
        Instant createdAt = Instant.parse("2026-10-01T07:30:00Z");
        auditLogPort.record(new AuditLogEntry(
                0L, 2L, AuditAction.EXECUTION_READ, "execution:exec-42", AuditResult.DENIED,
                "trace-denied-1", "cross-user read blocked", createdAt));

        List<AuditLogEntry> entries = auditLogPort.listByUserId(2L);

        AuditLogEntry entry = entries.stream()
                .filter(candidate -> "trace-denied-1".equals(candidate.traceId()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.action()).isEqualTo(AuditAction.EXECUTION_READ);
        assertThat(entry.resource()).isEqualTo("execution:exec-42");
        assertThat(entry.result()).isEqualTo(AuditResult.DENIED);
        assertThat(entry.detail()).isEqualTo("cross-user read blocked");
        assertThat(entry.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void returnsNewestFirstAndFiltersByUser() {
        long userId = 4L;
        auditLogPort.record(new AuditLogEntry(
                0L, userId, AuditAction.LOGIN, "platform.auth", AuditResult.SUCCESS,
                "trace-order-old", "", Instant.parse("2026-10-01T08:00:00Z")));
        auditLogPort.record(new AuditLogEntry(
                0L, userId, AuditAction.AGENT_RUN, "agent:patient-risk", AuditResult.FAILURE,
                "trace-order-new", "boom", Instant.parse("2026-10-01T09:00:00Z")));

        List<AuditLogEntry> entries = auditLogPort.listByUserId(userId);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).traceId()).isEqualTo("trace-order-new");
        assertThat(entries.get(1).traceId()).isEqualTo("trace-order-old");
    }

    @Test
    void returnsEmptyListInsteadOfNullForUnknownUser() {
        assertThat(auditLogPort.listByUserId(987654L)).isEmpty();
    }
}
