package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryKnowledgeBindingAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库绑定对账用例（Week 27）。
 *
 * <p>锁定的关键语义：改名同步、删库只标 MISSING 不删行、上游失败不改状态、
 * 新库只统计不绑定、租户隔离与 admin 权限。</p>
 */
class KnowledgeBindingReconcileUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T18:15:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long HOSPITAL_B_ADMIN_ID = 4L;
    private static final long DEFAULT_ENTERPRISE_USER = 7L;
    private static final long HOSPITAL_B_ENTERPRISE_USER = 8L;
    private static final long KB_ID = 11L;

    private InMemoryKnowledgeBindingAdapter bindings;
    private KnowledgeSearchPort searchPort;
    private InMemoryAuditLogAdapter auditLog;
    private RecordingObservabilityPort observability;
    private PlatformKnowledgeProperties properties;
    private KnowledgeBindingReconcileUseCase useCase;

    @BeforeEach
    void setUp() {
        bindings = new InMemoryKnowledgeBindingAdapter();
        searchPort = mock(KnowledgeSearchPort.class);
        auditLog = new InMemoryAuditLogAdapter();
        observability = new RecordingObservabilityPort();
        properties = new PlatformKnowledgeProperties();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(HOSPITAL_B_ADMIN_ID, "admin_b", "hash", "admin", "hospital-b"));
        useCase = new KnowledgeBindingReconcileUseCase(
                bindings, searchPort, new PlatformPermissionChecker(users, roles),
                auditLog, observability, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void syncsRenamedKnowledgeBase() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "旧名", "旧描述",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER,
                        entry(KB_ID, "新名", "新描述")));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.checked()).isEqualTo(1);
        KnowledgeBinding loaded = bindings.findById("b-1").orElseThrow();
        assertThat(loaded.name()).isEqualTo("新名");
        assertThat(loaded.description()).isEqualTo("新描述");
        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
        assertThat(loaded.lastSyncedAt()).isEqualTo(NOW);
    }

    @Test
    void marksMissingButDoesNotDeleteBinding() {
        Instant oldSynced = NOW.minusSeconds(86400);
        Instant oldUpdated = NOW.minusSeconds(172800);
        bindings.save(new KnowledgeBinding(
                "b-1", "default", DEFAULT_ENTERPRISE_USER, KB_ID, "慢病指南", "内部",
                true, ADMIN_ID, oldUpdated, oldUpdated, KnowledgeBindingSyncStatus.ACTIVE, oldSynced));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.missing()).isEqualTo(1);
        KnowledgeBinding loaded = bindings.findById("b-1").orElseThrow();
        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.MISSING);
        assertThat(loaded.lastSyncedAt()).isEqualTo(NOW);
        // 只刷新同步列：元数据更新时间不能被每日对账改掉
        assertThat(loaded.updatedAt()).isEqualTo(oldUpdated);
        assertThat(loaded.missing()).isTrue();
    }

    @Test
    void upstreamFailureKeepsStatusAndCountsFailed() {
        Instant oldSynced = NOW.minusSeconds(86400);
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, oldSynced));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenThrow(new ToolExecutionException("knowledge service unavailable"));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.missing()).isZero();
        KnowledgeBinding loaded = bindings.findById("b-1").orElseThrow();
        assertThat(loaded.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
        assertThat(loaded.lastSyncedAt()).isEqualTo(oldSynced);
    }

    @Test
    void failedGroupDoesNotBlockOtherGroups() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        bindings.save(binding("b-2", "hospital-b", HOSPITAL_B_ENTERPRISE_USER, "B 医院库", "",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenThrow(new ToolExecutionException("down"));
        when(searchPort.listBasesByEnterpriseUser(HOSPITAL_B_ENTERPRISE_USER))
                .thenReturn(catalog(HOSPITAL_B_ENTERPRISE_USER, entry(KB_ID, "B 医院库", "")));

        KnowledgeReconcileResult result = useCase.reconcileAll();

        assertThat(result.groups()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(bindings.findById("b-1").orElseThrow().syncStatus())
                .isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
    }

    @Test
    void reappearingBaseReturnsToActive() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.MISSING, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER, entry(KB_ID, "慢病指南", "内部")));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(bindings.findById("b-1").orElseThrow().syncStatus())
                .isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
    }

    @Test
    void unboundEnterpriseBasesAreCountedButNotBound() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER,
                        entry(KB_ID, "慢病指南", "内部"), entry(12L, "新库", "")));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.unbound()).isEqualTo(1);
        assertThat(bindings.listByTenant("default")).hasSize(1);
    }

    @Test
    void unchangedMetadataRefreshesLastSyncedWithoutTouchingUpdatedAt() {
        Instant oldUpdated = NOW.minusSeconds(172800);
        bindings.save(new KnowledgeBinding(
                "b-1", "default", DEFAULT_ENTERPRISE_USER, KB_ID, "慢病指南", "内部",
                true, ADMIN_ID, oldUpdated, oldUpdated, KnowledgeBindingSyncStatus.ACTIVE,
                NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER, entry(KB_ID, "慢病指南", "内部")));

        KnowledgeReconcileResult result = useCase.reconcileTenant(ADMIN_ID);

        assertThat(result.unchanged()).isEqualTo(1);
        KnowledgeBinding loaded = bindings.findById("b-1").orElseThrow();
        assertThat(loaded.lastSyncedAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(oldUpdated);
    }

    @Test
    void manualReconcileIsAdminOnly() {
        assertThatThrownBy(() -> useCase.reconcileTenant(OPERATOR_ID))
                .isInstanceOf(PlatformAccessDeniedException.class);
        verify(searchPort, never()).listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER);
    }

    @Test
    void manualReconcileIsTenantScoped() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        bindings.save(binding("b-2", "hospital-b", HOSPITAL_B_ENTERPRISE_USER, "B 医院库", "",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(HOSPITAL_B_ENTERPRISE_USER))
                .thenReturn(catalog(HOSPITAL_B_ENTERPRISE_USER, entry(KB_ID, "B 医院库", "")));

        KnowledgeReconcileResult result = useCase.reconcileTenant(HOSPITAL_B_ADMIN_ID);

        assertThat(result.tenantKey()).isEqualTo("hospital-b");
        assertThat(result.checked()).isEqualTo(1);
        verify(searchPort, never()).listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER);
    }

    @Test
    void manualReconcileWritesAudit() {
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER));

        useCase.reconcileTenant(ADMIN_ID);

        assertThat(auditLog.listAll())
                .extracting(AuditLogEntry::action)
                .containsExactly(AuditAction.KNOWLEDGE_RECONCILE);
        assertThat(auditLog.listAll().get(0).detail()).contains("missing=0");
    }

    @Test
    void scheduledReconcileCoversAllTenantsWithoutAudit() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        bindings.save(binding("b-2", "hospital-b", HOSPITAL_B_ENTERPRISE_USER, "B 医院库", "",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER, entry(KB_ID, "慢病指南", "内部")));
        when(searchPort.listBasesByEnterpriseUser(HOSPITAL_B_ENTERPRISE_USER))
                .thenReturn(catalog(HOSPITAL_B_ENTERPRISE_USER, entry(KB_ID, "B 医院库", "")));

        KnowledgeReconcileResult result = useCase.reconcileAll();

        assertThat(result.groups()).isEqualTo(2);
        assertThat(result.checked()).isEqualTo(2);
        assertThat(auditLog.listAll()).isEmpty();
    }

    @Test
    void recordsReconcileMetricsAndMissingGauge() {
        bindings.save(binding("b-1", "default", DEFAULT_ENTERPRISE_USER, "慢病指南", "内部",
                KnowledgeBindingSyncStatus.ACTIVE, NOW.minusSeconds(86400)));
        when(searchPort.listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER))
                .thenReturn(catalog(DEFAULT_ENTERPRISE_USER));

        useCase.reconcileTenant(ADMIN_ID);

        assertThat(observability.calls).contains(
                "recordCounter:knowledge.reconcile.count[knowledge.result=missing]",
                "recordGauge:knowledge.binding.missing.count=1.0");
    }

    @Test
    void scheduledReconcileIsSkippedWhenKnowledgeDisabled() {
        properties.setEnabled(false);

        KnowledgeReconcileResult result = useCase.reconcileAll();

        assertThat(result.checked()).isZero();
        verify(searchPort, never()).listBasesByEnterpriseUser(DEFAULT_ENTERPRISE_USER);
    }

    private KnowledgeBinding binding(
            String id,
            String tenantKey,
            long enterpriseUserId,
            String name,
            String description,
            KnowledgeBindingSyncStatus status,
            Instant lastSyncedAt
    ) {
        return new KnowledgeBinding(
                id, tenantKey, enterpriseUserId, KB_ID, name, description, true,
                ADMIN_ID, NOW.minusSeconds(259200), NOW.minusSeconds(259200), status, lastSyncedAt);
    }

    private KnowledgeBaseCatalog catalog(long enterpriseUserId, KnowledgeBaseCatalogEntry... entries) {
        return new KnowledgeBaseCatalog(enterpriseUserId, List.of(entries));
    }

    private KnowledgeBaseCatalogEntry entry(long knowledgeBaseId, String name, String description) {
        return new KnowledgeBaseCatalogEntry(knowledgeBaseId, name, description, 2);
    }
}
