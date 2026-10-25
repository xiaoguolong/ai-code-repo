package com.aicode.framework.platform.application;

import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
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
import static org.mockito.Mockito.when;

/**
 * 知识库绑定用例（Week 25）：导入幂等、租户隔离、admin 权限、删除范围。
 */
class KnowledgeBindingUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long HOSPITAL_B_ADMIN_ID = 4L;

    private InMemoryKnowledgeBindingAdapter bindingPort;
    private KnowledgeSearchPort searchPort;
    private InMemoryPlatformUserAdapter users;
    private PlatformPermissionChecker checker;
    private KnowledgeBindingUseCase useCase;
    private String importedBindingId;

    @BeforeEach
    void setUp() {
        bindingPort = new InMemoryKnowledgeBindingAdapter();
        searchPort = mock(KnowledgeSearchPort.class);
        users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(HOSPITAL_B_ADMIN_ID, "admin_b", "hash", "admin", "hospital-b"));
        checker = new PlatformPermissionChecker(users, roles);
        useCase = new KnowledgeBindingUseCase(
                bindingPort, searchPort, users, checker, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void importIsIdempotentAndStoresEnterpriseOwner() {
        when(searchPort.listBases(ADMIN_ID)).thenReturn(new KnowledgeBaseCatalog(7L, List.of(
                new KnowledgeBaseCatalogEntry(11L, "慢病指南", "内部", 3),
                new KnowledgeBaseCatalogEntry(12L, "用药规范", "内部", 1))));

        KnowledgeImportResult first = useCase.importFromEnterprise(ADMIN_ID);
        assertThat(first.enterpriseUserId()).isEqualTo(7L);
        assertThat(first.imported()).isEqualTo(2);
        assertThat(first.updated()).isZero();
        assertThat(first.bindings()).hasSize(2);

        KnowledgeImportResult second = useCase.importFromEnterprise(ADMIN_ID);
        assertThat(second.imported()).isZero();
        assertThat(second.updated()).isEqualTo(2);
        assertThat(bindingPort.listByTenant("default")).hasSize(2);
        assertThat(bindingPort.listByTenant("default"))
                .allSatisfy(binding -> assertThat(binding.enterpriseUserId()).isEqualTo(7L));
    }

    @Test
    void importUpdatesNameWhenEnterpriseRenames() {
        when(searchPort.listBases(ADMIN_ID)).thenReturn(new KnowledgeBaseCatalog(7L, List.of(
                new KnowledgeBaseCatalogEntry(11L, "旧名", "desc", 1))));
        useCase.importFromEnterprise(ADMIN_ID);
        when(searchPort.listBases(ADMIN_ID)).thenReturn(new KnowledgeBaseCatalog(7L, List.of(
                new KnowledgeBaseCatalogEntry(11L, "新名", "desc", 2))));

        useCase.importFromEnterprise(ADMIN_ID);

        assertThat(bindingPort.findByTenantAndKnowledgeBase("default", 11L).orElseThrow().name())
                .isEqualTo("新名");
    }

    @Test
    void importMarksBindingsActiveAndSynced() {
        adminImports();

        KnowledgeBinding binding = bindingPort.findByTenantAndKnowledgeBase("default", 11L).orElseThrow();
        assertThat(binding.syncStatus()).isEqualTo(KnowledgeBindingSyncStatus.ACTIVE);
        assertThat(binding.lastSyncedAt()).isEqualTo(NOW);
    }

    @Test
    void operatorCannotImportOrDelete() {
        when(searchPort.listBases(OPERATOR_ID)).thenReturn(new KnowledgeBaseCatalog(9L, List.of()));

        assertThatThrownBy(() -> useCase.importFromEnterprise(OPERATOR_ID))
                .isInstanceOf(PlatformAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.delete(OPERATOR_ID, "missing"))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void listIsScopedToCallersTenant() {
        adminImports();
        KnowledgeBinding hospitalBinding = new KnowledgeBinding(
                "b-h", "hospital-b", 8L, 21L, "B 医院库", "", true,
                HOSPITAL_B_ADMIN_ID, NOW, NOW);
        bindingPort.save(hospitalBinding);

        assertThat(useCase.list(ADMIN_ID)).extracting(KnowledgeBinding::bindingId)
                .containsExactly(importedBindingId);
        assertThat(useCase.list(HOSPITAL_B_ADMIN_ID)).extracting(KnowledgeBinding::bindingId)
                .containsExactly("b-h");
        assertThat(useCase.list(OPERATOR_ID)).hasSize(1);
    }

    @Test
    void deleteIsTenantScopedAndIdempotentIsNotFound() {
        adminImports();

        useCase.delete(ADMIN_ID, importedBindingId);
        assertThat(bindingPort.listByTenant("default")).isEmpty();
        assertThatThrownBy(() -> useCase.delete(ADMIN_ID, importedBindingId))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void cannotDeleteAnotherTenantsBinding() {
        bindingPort.save(new KnowledgeBinding(
                "b-h", "hospital-b", 8L, 21L, "B 医院库", "", true,
                HOSPITAL_B_ADMIN_ID, NOW, NOW));

        assertThatThrownBy(() -> useCase.delete(ADMIN_ID, "b-h"))
                .isInstanceOf(PlatformNotFoundException.class);
        assertThat(bindingPort.listByTenant("hospital-b")).hasSize(1);
    }

    private void adminImports() {
        when(searchPort.listBases(ADMIN_ID)).thenReturn(new KnowledgeBaseCatalog(7L, List.of(
                new KnowledgeBaseCatalogEntry(11L, "慢病指南", "内部", 3))));
        KnowledgeImportResult result = useCase.importFromEnterprise(ADMIN_ID);
        importedBindingId = result.bindings().get(0).bindingId();
    }
}
