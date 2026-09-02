package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.TestClockConfig;
import com.aicode.demo.domain.model.AuditStatus;
import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.infrastructure.persistence.entity.ChatSessionEntity;
import com.aicode.demo.infrastructure.persistence.repository.ChatSessionRepository;
import com.aicode.demo.infrastructure.persistence.repository.TokenRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditAdapter.class, TestClockConfig.class})
@ActiveProfiles("test")
class JpaAuditAdapterTest {

    @Autowired
    private JpaAuditAdapter adapter;

    @Autowired
    private TokenRecordRepository repository;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.save(new ChatSessionEntity("s1", "deepseek-chat", Instant.now()));
        sessionRepository.save(new ChatSessionEntity("s2", "deepseek-chat", Instant.now()));
    }

    @Test
    void shouldPersistTokenRecord_whenRecordingAudit() {
        ChatAuditRecord record = new ChatAuditRecord(
                Instant.parse("2026-09-02T00:00:00Z"),
                "s1",
                "deepseek-chat",
                "hi",
                "hello",
                10,
                5,
                15,
                100L,
                AuditStatus.SUCCESS,
                null
        );

        adapter.record(record);

        assertThat(repository.findAll()).hasSize(1);
        var entity = repository.findAll().get(0);
        assertThat(entity.sessionId()).isEqualTo("s1");
        assertThat(entity.totalTokens()).isEqualTo(15);
        assertThat(entity.status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldAggregateTokenStats() {
        adapter.record(new ChatAuditRecord(
                Instant.now(), "s1", "m", "a", "b", 10, 5, 15, 1L, AuditStatus.SUCCESS, null
        ));
        adapter.record(new ChatAuditRecord(
                Instant.now(), "s2", "m", "c", "d", 2, 3, 5, 1L, AuditStatus.SUCCESS, null
        ));

        TokenStats stats = adapter.summary();

        assertThat(stats.requestCount()).isEqualTo(2);
        assertThat(stats.promptTokens()).isEqualTo(12);
        assertThat(stats.totalTokens()).isEqualTo(20);
    }
}
