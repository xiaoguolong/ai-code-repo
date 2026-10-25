package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.EmbeddingException;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformMemoryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 长期记忆生命周期（Week 23）：TTL 可见性、按范围删除、过期清理、向量重建。
 */
class PlatformMemoryLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final PlatformMemoryProperties MEMORY_PROPERTIES =
            new PlatformMemoryProperties(500, 0.05, 5, 20);
    private static final EmbeddingProperties EMBEDDING_PROPERTIES =
            new EmbeddingProperties("hashing", "", "", "demo-embed", 16, 10);
    private static final PlatformMemoryLifecycleProperties LIFECYCLE =
            new PlatformMemoryLifecycleProperties(30, false, "", 100);

    private InMemoryPlatformMemoryAdapter memories;
    private InMemoryPlatformUserAdapter users;
    private InMemoryAuditLogAdapter audit;
    private HashingEmbeddingAdapter embedder;
    private MemoryEmbeddingIdentity identity;
    private PlatformPermissionChecker checker;
    private PlatformMemoryUseCase memoryUseCase;
    private PlatformMemoryMaintenanceUseCase maintenance;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "operator2", "hash", "operator"));
        users.save(new PlatformUser(4L, "operator3", "hash", "operator"));

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        memories = new InMemoryPlatformMemoryAdapter(clock);
        embedder = new HashingEmbeddingAdapter(EMBEDDING_PROPERTIES);
        identity = new MemoryEmbeddingIdentity(EMBEDDING_PROPERTIES);
        checker = new PlatformPermissionChecker(users, roles);
        audit = new InMemoryAuditLogAdapter();
        memoryUseCase = memoryUseCase(LIFECYCLE, clock, embedder);
        maintenance = new PlatformMemoryMaintenanceUseCase(
                memoryUseCase, memories, checker, embedder, LIFECYCLE, identity, audit, clock);
    }

    @Test
    void saveSetsTtlAndEmbeddingModel() {
        PlatformMemoryRecord saved = memoryUseCase.save(scope(2L), "P001", "血糖控制", "报告");

        assertThat(saved.expiresAt()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(saved.embeddingModel()).isEqualTo("hashing:demo-embed:16");
        assertThat(memoryUseCase.list(2L, null, null, null, 5)).hasSize(1);
    }

    @Test
    void zeroTtlNeverExpires() {
        PlatformMemoryUseCase noTtl = memoryUseCase(
                new PlatformMemoryLifecycleProperties(0, false, "", 100),
                Clock.fixed(NOW, ZoneOffset.UTC), embedder);

        PlatformMemoryRecord saved = noTtl.save(scope(2L), "P001", "血糖控制", "报告");

        assertThat(saved.expiresAt()).isNull();
        assertThat(noTtl.list(2L, null, null, null, 5)).hasSize(1);
    }

    @Test
    void expiredRecordsAreInvisibleToListsAndSearch() {
        memories.save(record("m-expired", "血糖控制", NOW.minus(1, ChronoUnit.SECONDS)), vector());
        memories.save(record("m-live", "血糖控制", NOW.plus(1, ChronoUnit.HOURS)), vector());

        assertThat(memoryUseCase.list(2L, null, null, null, 10))
                .extracting(hit -> hit.record().memoryId())
                .containsExactly("m-live");
        assertThat(memoryUseCase.list(2L, null, null, "血糖控制", 10))
                .extracting(hit -> hit.record().memoryId())
                .containsExactly("m-live");
    }

    @Test
    void purgeExpiredDeletesOnlyExpiredRecords() {
        memories.save(record("m-expired", "任务 A", NOW.minus(1, ChronoUnit.SECONDS)), vector());
        memories.save(record("m-live", "任务 B", NOW.plus(1, ChronoUnit.DAYS)), vector());

        MemoryPurgeResult result =
                maintenance.purgeExpired(1L, 100);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(memories.size()).isEqualTo(1);
        assertThat(memoryUseCase.list(2L, null, null, null, 10)).hasSize(1);
    }

    @Test
    void nonAdminCannotPurgeExpired() {
        assertThatThrownBy(() -> maintenance.purgeExpired(2L, 100))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void deletesOwnMemory() {
        PlatformMemoryRecord saved = memoryUseCase.save(scope(2L), "P001", "任务", "答案");

        maintenance.delete(2L, null, saved.memoryId());

        assertThat(memoryUseCase.list(2L, null, null, null, 10)).isEmpty();
    }

    @Test
    void deleteOutsideScopeIsNotFound() {
        PlatformMemoryRecord saved = memoryUseCase.save(scope(2L), "P001", "任务", "答案");

        assertThatThrownBy(() -> maintenance.delete(3L, null, saved.memoryId()))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void nonAdminCannotDeleteAnotherUsersMemory() {
        assertThatThrownBy(() -> maintenance.delete(2L, 4L, "m-any"))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void adminCanDeleteAnotherUsersMemory() {
        PlatformMemoryRecord saved = memoryUseCase.save(scope(2L), "P001", "任务", "答案");

        maintenance.delete(1L, 2L, saved.memoryId());

        assertThat(memoryUseCase.list(2L, null, null, null, 10)).isEmpty();
    }

    @Test
    void rebuildsStaleEmbeddingAndMakesMemorySearchable() {
        memories.save(new PlatformMemoryRecord("m-stale", "default", 2L, "P001",
                "血糖控制", "答案", NOW, NOW.plus(1, ChronoUnit.DAYS), null), new float[0]);

        MemoryRebuildResult result =
                maintenance.rebuildEmbeddings(1L, 100);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.rebuilt()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.currentModel()).isEqualTo("hashing:demo-embed:16");
        assertThat(memories.listStaleEmbeddings("hashing:demo-embed:16", 100)).isEmpty();
        assertThat(memoryUseCase.list(2L, null, null, "血糖控制", 5))
                .extracting(hit -> hit.record().memoryId())
                .containsExactly("m-stale");
    }

    @Test
    void rebuildContinuesWhenOneEmbeddingFails() {
        EmbeddingModelPort failingOnBad = text -> {
            if ("bad".equals(text)) {
                throw new EmbeddingException("embedding down");
            }
            return embedder.embed(text);
        };
        PlatformMemoryMaintenanceUseCase failing = new PlatformMemoryMaintenanceUseCase(
                memoryUseCase, memories, checker, failingOnBad, LIFECYCLE, identity, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        memories.save(new PlatformMemoryRecord("m-good", "default", 2L, "P001",
                "good", "答案", NOW, NOW.plus(1, ChronoUnit.DAYS), null), new float[0]);
        memories.save(new PlatformMemoryRecord("m-bad", "default", 2L, "P001",
                "bad", "答案", NOW, NOW.plus(1, ChronoUnit.DAYS), null), new float[0]);

        MemoryRebuildResult result =
                failing.rebuildEmbeddings(1L, 100);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.rebuilt()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(memories.listStaleEmbeddings("hashing:demo-embed:16", 100))
                .extracting(PlatformMemoryRecord::memoryId)
                .containsExactly("m-bad");
    }

    @Test
    void nonAdminCannotRebuildEmbeddings() {
        assertThatThrownBy(() -> maintenance.rebuildEmbeddings(2L, 100))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void batchLimitIsClampedTo500AndDefaultsTo100() {
        for (int i = 0; i < 3; i++) {
            memories.save(new PlatformMemoryRecord("m-" + i, "default", 2L, "P001",
                    "任务 " + i, "答案", NOW, NOW.plus(1, ChronoUnit.DAYS), null), new float[0]);
        }

        assertThat(maintenance.rebuildEmbeddings(1L, 0).scanned()).isEqualTo(3);
        assertThat(maintenance.rebuildEmbeddings(1L, 10_000).scanned()).isZero();
    }

    private PlatformMemoryUseCase memoryUseCase(
            PlatformMemoryLifecycleProperties lifecycle,
            Clock clock,
            EmbeddingModelPort embedderPort
    ) {
        return new PlatformMemoryUseCase(
                memories, users, checker, embedderPort, MEMORY_PROPERTIES, lifecycle, clock, identity);
    }

    private PlatformMemoryScope scope(long userId) {
        return new PlatformMemoryScope("default", userId);
    }

    private PlatformMemoryRecord record(String memoryId, String task, Instant expiresAt) {
        return new PlatformMemoryRecord(memoryId, "default", 2L, "P001",
                task, "答案", NOW, expiresAt, "hashing:demo-embed:16");
    }

    private float[] vector() {
        return embedder.embed("血糖控制");
    }
}
