package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformTenantAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 租户用例测试：管理员可见性、未知租户、停用语义。 */
class PlatformTenantUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-24T00:00:00Z");

    private InMemoryPlatformTenantAdapter tenants;
    private PlatformTenantUseCase useCase;

    @BeforeEach
    void setUp() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        tenants = new InMemoryPlatformTenantAdapter();
        useCase = new PlatformTenantUseCase(tenants, new PlatformPermissionChecker(users, roles));

        roles.save(new PlatformRole("admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营人员", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));

        tenants.save(new PlatformTenant("default", "默认租户", TenantStatus.ACTIVE, CREATED_AT));
        tenants.save(new PlatformTenant("hospital-b", "演示医院B", TenantStatus.SUSPENDED, CREATED_AT));
    }

    @Test
    void adminListsAllTenants() {
        assertThat(useCase.listTenants(1L))
                .extracting(PlatformTenant::tenantKey)
                .containsExactly("default", "hospital-b");
    }

    @Test
    void rejectsNonAdminTenantList() {
        assertThatThrownBy(() -> useCase.listTenants(2L))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void findsTenantByKey() {
        PlatformTenant tenant = useCase.getTenant("default");

        assertThat(tenant.displayName()).isEqualTo("默认租户");
        assertThat(tenant.active()).isTrue();
    }

    @Test
    void rejectsUnknownTenant() {
        assertThatThrownBy(() -> useCase.getTenant("no-such-tenant"))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void suspendedTenantIsNotActiveWhileUnknownTenantIsAlsoInactive() {
        assertThat(tenants.isActive("default")).isTrue();
        assertThat(tenants.isActive("hospital-b")).isFalse();
        assertThat(tenants.isActive("no-such-tenant")).isFalse();
    }

    @Test
    void tenantModelRejectsBlankFields() {
        assertThatThrownBy(() -> new PlatformTenant(" ", "租户", TenantStatus.ACTIVE, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformTenant("t1", " ", TenantStatus.ACTIVE, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenantStatusFallsBackToActiveForUnknownValues() {
        assertThat(TenantStatus.from("suspended")).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(TenantStatus.from(null)).isEqualTo(TenantStatus.ACTIVE);
        assertThat(TenantStatus.from("weird")).isEqualTo(TenantStatus.ACTIVE);
    }
}
