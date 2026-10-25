package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.application.PlatformMemoryUseCase;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 长期记忆生命周期落库（H2 + Flyway V6）：TTL 列、过期过滤、清理、按范围删除、向量标记/重建。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.ttl-days=30",
        "platform.memory.lifecycle.cleanup-enabled=false"
})
@ActiveProfiles("test")
class JdbcPlatformMemoryLifecycleTest {

    private static final String TENANT = "w23-life";
    private static final long USER_ID = 9701L;
    private static final PlatformMemoryScope SCOPE = new PlatformMemoryScope(TENANT, USER_ID);
    private static final float[] VECTOR = {1f, 0f, 0f};

    @Autowired
    private PlatformMemoryPort memoryPort;

    @Autowired
    private PlatformMemoryUseCase memoryUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM platform_memory WHERE tenant_key = ?", TENANT);
    }

    @Test
    void saveWritesTtlAndEmbeddingModel() {
        Instant before = Instant.now();

        PlatformMemoryRecord saved = memoryUseCase.save(SCOPE, "P001", "血糖控制", "报告");

        assertThat(saved.expiresAt()).isNotNull();
        assertThat(saved.expiresAt()).isAfter(before.plus(29, ChronoUnit.DAYS));
        assertThat(saved.expiresAt()).isBefore(before.plus(31, ChronoUnit.DAYS));
        assertThat(saved.embeddingModel()).startsWith("hashing:");

        PlatformMemoryRecord loaded = memoryPort.listRecent(SCOPE, 10).get(0);
        assertThat(loaded.expiresAt()).isNotNull();
        assertThat(loaded.embeddingModel()).isEqualTo(saved.embeddingModel());
    }

    @Test
    void expiredRowsAreInvisibleToListAndSearch() {
        Instant now = Instant.now();
        memoryPort.save(record("m-expired", "血糖控制", now.minus(1, ChronoUnit.HOURS)), VECTOR);
        memoryPort.save(record("m-live", "血糖控制", now.plus(1, ChronoUnit.DAYS)), VECTOR);

        assertThat(memoryPort.listRecent(SCOPE, 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-live");
        assertThat(memoryPort.listBySession(SCOPE, "P001", 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-live");
        assertThat(memoryPort.search(SCOPE, VECTOR, 100, 5, 0.0))
                .extracting(hit -> hit.record().memoryId())
                .containsExactly("m-live");
    }

    @Test
    void purgeExpiredDeletesOnlyExpiredRows() {
        Instant now = Instant.now();
        memoryPort.save(record("m-expired", "任务", now.minus(1, ChronoUnit.HOURS)), VECTOR);
        memoryPort.save(record("m-never", "任务", null), VECTOR);
        memoryPort.save(record("m-live", "任务", now.plus(1, ChronoUnit.DAYS)), VECTOR);

        int deleted = memoryPort.purgeExpired(now, 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(memoryPort.listRecent(SCOPE, 10))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactlyInAnyOrder("m-never", "m-live");
    }

    @Test
    void deleteIsScopedByTenantAndUser() {
        PlatformMemoryRecord saved = memoryUseCase.save(SCOPE, "P001", "任务", "答案");
        PlatformMemoryScope otherUser = new PlatformMemoryScope(TENANT, USER_ID + 1);

        assertThat(memoryPort.delete(otherUser, saved.memoryId())).isFalse();
        assertThat(memoryPort.delete(SCOPE, saved.memoryId())).isTrue();
        assertThat(memoryPort.listRecent(SCOPE, 10)).isEmpty();
    }

    @Test
    void staleEmbeddingsCanBeFoundAndUpdated() {
        Instant now = Instant.now();
        memoryPort.save(new PlatformMemoryRecord("m-stale", TENANT, USER_ID, "P001",
                "血糖控制", "答案", now, now.plus(1, ChronoUnit.DAYS), null), new float[0]);

        assertThat(memoryPort.listStaleEmbeddings("hashing:default:1024", 100))
                .extracting(PlatformMemoryRecord::memoryId)
                .contains("m-stale");
        assertThat(memoryPort.updateEmbedding("m-stale", VECTOR, "hashing:default:1024")).isTrue();

        assertThat(memoryPort.listStaleEmbeddings("hashing:default:1024", 100))
                .extracting(PlatformMemoryRecord::memoryId)
                .doesNotContain("m-stale");
        assertThat(memoryPort.search(SCOPE, VECTOR, 100, 5, 0.0)).hasSize(1);
    }

    private PlatformMemoryRecord record(String memoryId, String task, Instant expiresAt) {
        return new PlatformMemoryRecord(
                memoryId,
                TENANT, USER_ID, "P001", task, "答案",
                Instant.now().minus(1, ChronoUnit.DAYS),
                expiresAt,
                "hashing:default:1024");
    }
}
