package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.EmbeddingException;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.aicode.core.infrastructure.embedding.HashingEmbeddingAdapter;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformMemoryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 长期记忆用例测试：租户/用户隔离、管理员换 scope、limit 收敛、语义排序、向量化失败降级。
 */
class PlatformMemoryUseCaseTest {

    private static final PlatformMemoryProperties PROPERTIES =
            new PlatformMemoryProperties(500, 0.05, 5, 20);

    private InMemoryPlatformMemoryAdapter memories;
    private InMemoryPlatformUserAdapter users;
    private PlatformMemoryUseCase useCase;

    @BeforeEach
    void setUp() {
        memories = new InMemoryPlatformMemoryAdapter();
        users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "operator2", "hash", "operator"));
        users.save(new PlatformUser(4L, "doctor-b", "hash", "operator", "hospital-b"));

        useCase = new PlatformMemoryUseCase(
                memories,
                users,
                new PlatformPermissionChecker(users, roles),
                new HashingEmbeddingAdapter(new EmbeddingProperties("hashing", "", "", "test-embed", 16, 10)),
                PROPERTIES);
    }

    @Test
    void savesWithTenantAndUserFromScope() {
        PlatformMemoryRecord saved = useCase.save(
                new PlatformMemoryScope("default", 2L), "P001", "为患者 P001 生成分析报告", "报告正文");

        assertThat(saved.memoryId()).isNotBlank();
        assertThat(saved.tenantKey()).isEqualTo("default");
        assertThat(saved.userId()).isEqualTo(2L);
        assertThat(saved.sessionId()).isEqualTo("P001");
        assertThat(useCase.list(2L, null, null, null, 5)).hasSize(1);
    }

    @Test
    void isolatesMemoriesBetweenUsersOfSameTenant() {
        useCase.save(new PlatformMemoryScope("default", 2L), "P001", "任务 A", "答案 A");

        assertThat(useCase.list(3L, null, null, null, 5)).isEmpty();
    }

    @Test
    void isolatesMemoriesBetweenTenantsForSamePatientId() {
        useCase.save(new PlatformMemoryScope("hospital-b", 4L), "P001", "任务 B", "答案 B");

        assertThat(useCase.list(2L, null, "P001", null, 5)).isEmpty();
        assertThat(useCase.list(4L, null, "P001", null, 5)).hasSize(1);
    }

    @Test
    void adminCanReadAnotherUsersMemories() {
        useCase.save(new PlatformMemoryScope("hospital-b", 4L), "P001", "任务 B", "答案 B");

        assertThat(useCase.list(1L, 4L, null, null, 5)).hasSize(1);
    }

    @Test
    void rejectsNonAdminReadingAnotherUsersMemories() {
        assertThatThrownBy(() -> useCase.list(2L, 4L, null, null, 5))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void rejectsUnknownTargetUserForAdmin() {
        assertThatThrownBy(() -> useCase.list(1L, 99L, null, null, 5))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void clampsLimitToConfiguredMaximum() {
        for (int i = 0; i < 25; i++) {
            useCase.save(new PlatformMemoryScope("default", 2L), "P001", "任务 " + i, "答案 " + i);
        }

        assertThat(useCase.list(2L, null, null, null, 100)).hasSize(20);
        assertThat(useCase.list(2L, null, null, null, 0)).hasSize(5);
    }

    @Test
    void ranksByQuerySimilarityAndCarriesScore() {
        useCase.save(new PlatformMemoryScope("default", 2L), "P001", "患者 P001 血压偏高需要复诊", "建议复诊");
        useCase.save(new PlatformMemoryScope("default", 2L), "P002", "患者 P002 血糖平稳继续观察", "继续观察");

        List<PlatformMemoryHit> hits = useCase.list(2L, null, null, "患者 P001 血压偏高需要复诊", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).record().task()).contains("血压偏高");
        assertThat(hits.get(0).score()).isGreaterThanOrEqualTo(0.99);
        assertThat(hits).allSatisfy(hit -> assertThat(hit.score()).isGreaterThanOrEqualTo(PROPERTIES.resolvedMinScore()));
    }

    @Test
    void filtersByPatientSession() {
        useCase.save(new PlatformMemoryScope("default", 2L), "P001", "任务 P001", "答案");
        useCase.save(new PlatformMemoryScope("default", 2L), "P002", "任务 P002", "答案");

        assertThat(useCase.list(2L, null, "P002", null, 10))
                .extracting(hit -> hit.record().sessionId())
                .containsExactly("P002");

        assertThat(useCase.list(2L, null, "  ", null, 10)).hasSize(2);
    }

    @Test
    void embeddingFailureDoesNotBreakSaveNorSearch() {
        PlatformMemoryUseCase degraded = new PlatformMemoryUseCase(
                memories,
                users,
                new PlatformPermissionChecker(users, roles()),
                failingEmbedder(),
                PROPERTIES);

        PlatformMemoryRecord saved = degraded.save(
                new PlatformMemoryScope("default", 2L), "P001", "任务 C", "答案 C");

        assertThat(saved.memoryId()).isNotBlank();
        assertThat(degraded.list(2L, null, null, null, 5)).hasSize(1);
        assertThat(degraded.list(2L, null, null, "任务 C", 5)).isEmpty();
    }

    private InMemoryPlatformRoleAdapter roles() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        return roles;
    }

    private EmbeddingModelPort failingEmbedder() {
        return text -> {
            throw new EmbeddingException("embedding service down");
        };
    }
}
