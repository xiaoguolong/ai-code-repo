package com.aicode.demo.infrastructure.audit;

import com.aicode.demo.domain.model.AuditStatus;
import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuditAdapterTest {

    @Test
    void shouldAccumulateTokenStats() {
        InMemoryAuditAdapter adapter = new InMemoryAuditAdapter();
        adapter.record(record(10, 5, 15, AuditStatus.SUCCESS));
        adapter.record(record(4, 6, 10, AuditStatus.FAILED));

        TokenStats stats = adapter.summary();

        assertThat(stats.requestCount()).isEqualTo(2);
        assertThat(stats.promptTokens()).isEqualTo(14);
        assertThat(stats.completionTokens()).isEqualTo(11);
        assertThat(stats.totalTokens()).isEqualTo(25);
    }

    private static ChatAuditRecord record(int prompt, int completion, int total, AuditStatus status) {
        return new ChatAuditRecord(
                Instant.parse("2026-08-28T00:00:00Z"),
                "s1",
                "deepseek-chat",
                "in",
                "out",
                prompt,
                completion,
                total,
                12L,
                status,
                status == AuditStatus.FAILED ? "CHAT_MODEL_ERROR" : null
        );
    }
}
