package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库绑定落库适配器（Week 27，H2 MODE=PostgreSQL + Flyway V10）。
 *
 * <p>重点：同步列写入/读取、{@code updateSyncState} 不改 {@code updated_at}、
 * 旧构造器（10 参）落库后为 {@code UNKNOWN}。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "platform.knowledge.reconcile.enabled=false"
})
@ActiveProfiles("test")
class JdbcKnowledgeBindingAdapterTest {

    private static final long USER_ID = 9811L;
    private static final Instant NOW = Instant.parse("2026-10-25T18:15:00Z");

    @Autowired
    private KnowledgeBindingPort bindingPort;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM platform_knowledge_binding WHERE tenant_key = 'w27-tenant'");
        userPort.save(new PlatformUser(USER_ID, "w27-binding-user", "hash", "operator"));
    }

    @Test
    void savesAndReadsSyncColumns() {
        bindingPort.save(binding("w27-b1", 101L, KnowledgeBindingSyncStatus.ACTIVE, NOW));

        KnowledgeBinding loaded = bindingPort.findById("w27-b1").orElseThrow();

        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
        assertThat(loaded.lastSyncedAt()).isEqualTo(NOW);
        assertThat(loaded.name()).isEqualTo("慢病指南");
    }

    @Test
    void legacyTenArgumentBindingDefaultsToUnknown() {
        bindingPort.save(new KnowledgeBinding(
                "w27-b2", "w27-tenant", 7L, 102L, "旧行", "", true,
                USER_ID, NOW.minusSeconds(60), NOW.minusSeconds(60)));

        KnowledgeBinding loaded = bindingPort.findById("w27-b2").orElseThrow();

        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.UNKNOWN);
        assertThat(loaded.lastSyncedAt()).isNull();
    }

    @Test
    void updateSyncStateOnlyTouchesSyncColumns() {
        Instant oldUpdated = NOW.minusSeconds(7200);
        bindingPort.save(new KnowledgeBinding(
                "w27-b3", "w27-tenant", 7L, 103L, "慢病指南", "内部", true,
                USER_ID, oldUpdated, oldUpdated, KnowledgeBindingSyncStatus.ACTIVE,
                NOW.minusSeconds(3600)));

        boolean updated = bindingPort.updateSyncState(
                "w27-b3", KnowledgeBindingSyncStatus.MISSING, NOW);

        assertThat(updated).isTrue();
        KnowledgeBinding loaded = bindingPort.findById("w27-b3").orElseThrow();
        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.MISSING);
        assertThat(loaded.lastSyncedAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(oldUpdated);
    }

    @Test
    void listsAllAndCountsByStatus() {
        bindingPort.save(binding("w27-b4", 104L, KnowledgeBindingSyncStatus.ACTIVE, NOW));
        bindingPort.save(binding("w27-b5", 105L, KnowledgeBindingSyncStatus.MISSING, NOW));
        long missingBefore = bindingPort.countBySyncStatus(KnowledgeBindingSyncStatus.MISSING);
        bindingPort.updateSyncState("w27-b4", KnowledgeBindingSyncStatus.MISSING, NOW);

        assertThat(bindingPort.listAll()).extracting(KnowledgeBinding::bindingId)
                .contains("w27-b4", "w27-b5");
        assertThat(bindingPort.countBySyncStatus(KnowledgeBindingSyncStatus.MISSING))
                .isEqualTo(missingBefore + 1);
    }

    private KnowledgeBinding binding(
            String id,
            long knowledgeBaseId,
            KnowledgeBindingSyncStatus status,
            Instant syncedAt
    ) {
        return new KnowledgeBinding(
                id, "w27-tenant", 7L, knowledgeBaseId, "慢病指南", "内部", true,
                USER_ID, NOW.minusSeconds(3600), NOW.minusSeconds(3600), status, syncedAt);
    }
}
