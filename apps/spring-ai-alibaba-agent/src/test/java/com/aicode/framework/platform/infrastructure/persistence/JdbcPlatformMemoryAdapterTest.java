package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 长期记忆落库适配器（H2 MODE=PostgreSQL + Flyway V5）。
 *
 * <p>重点不是「能存能读」，而是**隔离在 SQL 谓词里**：同租户他人、同用户他人租户都必须查不到。</p>
 *
 * <p>租户键/用户 ID 全部用本类专属值（{@code jdbc-mem-*} / 91xx）：本仓库所有
 * {@code @SpringBootTest} 共用同一个 H2 内存库，用 {@code default} 之类公共范围会让断言
 * 受其它测试写入的数据影响（实测：与 HTTP 测试同 JVM 跑时列表多出一条）。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcPlatformMemoryAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-24T02:00:00Z");

    @Autowired
    private PlatformMemoryPort memoryPort;

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(memoryPort).isInstanceOf(JdbcPlatformMemoryAdapter.class);
    }

    @Test
    void listsOnlyRecordsOfRequestedScope() {
        PlatformMemoryScope scopeA = new PlatformMemoryScope("jdbc-mem-a", 9101L);
        PlatformMemoryScope otherUser = new PlatformMemoryScope("jdbc-mem-a", 9102L);
        PlatformMemoryScope otherTenant = new PlatformMemoryScope("jdbc-mem-b", 9101L);

        memoryPort.save(record("m-scope-a", "jdbc-mem-a", 9101L, "P001"), vector());
        memoryPort.save(record("m-scope-other-user", "jdbc-mem-a", 9102L, "P001"), vector());
        memoryPort.save(record("m-scope-other-tenant", "jdbc-mem-b", 9101L, "P001"), vector());

        assertThat(memoryPort.listRecent(scopeA, 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-scope-a");
        assertThat(memoryPort.listRecent(otherUser, 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-scope-other-user");
        assertThat(memoryPort.listRecent(otherTenant, 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-scope-other-tenant");
    }

    @Test
    void filtersBySessionInsideScopeOnly() {
        PlatformMemoryScope scope = new PlatformMemoryScope("jdbc-mem-session", 9201L);
        memoryPort.save(record("m-session-p1", "jdbc-mem-session", 9201L, "P001"), vector());
        memoryPort.save(record("m-session-p2", "jdbc-mem-session", 9201L, "P002"), vector());
        memoryPort.save(record("m-session-p1-other", "jdbc-mem-session", 9202L, "P001"), vector());

        assertThat(memoryPort.listBySession(scope, "P001", 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-session-p1");
    }

    @Test
    void searchNeverLeaksAcrossScopes() {
        PlatformMemoryScope scope = new PlatformMemoryScope("jdbc-mem-search", 9301L);
        memoryPort.save(record("m-search-mine", "jdbc-mem-search", 9301L, "P001"), vector());
        memoryPort.save(record("m-search-other", "jdbc-mem-search", 9302L, "P001"), vector());

        List<PlatformMemoryHit> hits = memoryPort.search(scope, vector(), 100, 5, 0.0);

        assertThat(hits).extracting(hit -> hit.record().memoryId()).containsExactly("m-search-mine");
    }

    @Test
    void returnsNewestFirstAndRespectsLimit() {
        PlatformMemoryScope scope = new PlatformMemoryScope("jdbc-mem-recent", 9401L);
        memoryPort.save(record("m-old", "jdbc-mem-recent", 9401L, "P001", CREATED_AT), vector());
        memoryPort.save(record("m-new", "jdbc-mem-recent", 9401L, "P001", CREATED_AT.plusSeconds(60)), vector());

        assertThat(memoryPort.listRecent(scope, 1))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-new");
    }

    private PlatformMemoryRecord record(String memoryId, String tenantKey, long userId, String sessionId) {
        return record(memoryId, tenantKey, userId, sessionId, CREATED_AT);
    }

    private PlatformMemoryRecord record(String memoryId, String tenantKey, long userId, String sessionId,
                                        Instant createdAt) {
        return new PlatformMemoryRecord(memoryId, tenantKey, userId, sessionId,
                "task-" + memoryId, "answer-" + memoryId, createdAt);
    }

    private float[] vector() {
        return new float[]{1f, 0f, 0f};
    }
}
