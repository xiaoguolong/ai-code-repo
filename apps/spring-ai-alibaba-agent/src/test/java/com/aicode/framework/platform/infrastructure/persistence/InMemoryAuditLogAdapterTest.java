package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内审计日志适配器（platform.persistence.mode=memory）。
 * 无库启动 / 无库单测时使用，保证审计调用点始终有可注入实现。
 */
class InMemoryAuditLogAdapterTest {

    private final InMemoryAuditLogAdapter adapter = new InMemoryAuditLogAdapter();

    @Test
    void recordsLoginAuditEntryWithTraceId() {
        adapter.record(new AuditLogEntry(
                0L, 2L, AuditAction.LOGIN, "platform.auth", AuditResult.SUCCESS,
                "trace-1", "", Instant.parse("2026-10-01T00:00:00Z")));

        List<AuditLogEntry> entries = adapter.listAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo(AuditAction.LOGIN);
        assertThat(entries.get(0).traceId()).isEqualTo("trace-1");
        assertThat(entries.get(0).result()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void filtersEntriesByUserIdAndReturnsNewestFirst() {
        adapter.record(entry(2L, AuditAction.LOGIN, Instant.parse("2026-10-01T01:00:00Z")));
        adapter.record(entry(2L, AuditAction.AGENT_RUN, Instant.parse("2026-10-01T03:00:00Z")));
        adapter.record(entry(3L, AuditAction.LOGIN, Instant.parse("2026-10-01T02:00:00Z")));

        assertThat(adapter.listByUserId(2L))
                .extracting(AuditLogEntry::action)
                .containsExactly(AuditAction.AGENT_RUN, AuditAction.LOGIN);
        assertThat(adapter.listAll()).hasSize(3);
    }

    @Test
    void returnsEmptyListInsteadOfNullForUnknownUser() {
        assertThat(adapter.listByUserId(999L)).isEmpty();
        assertThat(adapter.listAll()).isEmpty();
    }

    private AuditLogEntry entry(long userId, AuditAction action, Instant createdAt) {
        return new AuditLogEntry(0L, userId, action, "resource", AuditResult.SUCCESS, "t", "", createdAt);
    }
}
