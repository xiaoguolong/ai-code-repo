package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.AuditStatus;
import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.infrastructure.persistence.entity.ChatSessionEntity;
import com.aicode.demo.infrastructure.persistence.mapper.ChatSessionMapper;
import com.aicode.demo.infrastructure.persistence.mapper.TokenRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MyBatisAuditAdapterTest {

    @Autowired
    private MyBatisAuditAdapter adapter;

    @Autowired
    private TokenRecordMapper tokenRecordMapper;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @BeforeEach
    void setUp() {
        sessionMapper.insert(new ChatSessionEntity("s1", "deepseek-chat", Instant.now()));
        sessionMapper.insert(new ChatSessionEntity("s2", "deepseek-chat", Instant.now()));
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

        assertThat(tokenRecordMapper.listByMap(false, Map.of("sessionId", "s1"))).hasSize(1);
        var entity = tokenRecordMapper.listByMap(false, Map.of("sessionId", "s1")).get(0);
        assertThat(entity.getSessionId()).isEqualTo("s1");
        assertThat(entity.getTotalTokens()).isEqualTo(15);
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
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
