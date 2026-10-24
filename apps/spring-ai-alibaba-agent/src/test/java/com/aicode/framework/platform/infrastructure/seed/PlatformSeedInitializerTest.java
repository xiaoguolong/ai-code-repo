package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.infrastructure.config.AuthProperties;
import com.aicode.core.infrastructure.security.SaTokenMd5PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformTenantAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户与第二租户用户 seed（Week 21）：**按 key / id 逐个判空**，不整体跳过、不覆盖运维改动。
 *
 * <p>这两个用例是为真库暴露的问题补的：老库（已有 admin/operator/viewer）启动时，
 * 整体判空的 RBAC seed 会让新增的 {@code doctor_b} 永远建不出来（实测 3 行用户、无 104）。</p>
 */
class PlatformSeedInitializerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void tenantSeedCreatesBothTenantsWhenMissing() {
        InMemoryPlatformTenantAdapter tenants = new InMemoryPlatformTenantAdapter();

        new PlatformTenantSeedInitializer(tenants).run(new DefaultApplicationArguments());

        assertThat(tenants.findByKey("default")).isPresent();
        assertThat(tenants.findByKey("hospital-b")).isPresent();
    }

    @Test
    void tenantSeedDoesNotOverwriteExistingTenantState() {
        InMemoryPlatformTenantAdapter tenants = new InMemoryPlatformTenantAdapter();
        tenants.save(new PlatformTenant("hospital-b", "运维改名", TenantStatus.SUSPENDED, CREATED_AT));

        new PlatformTenantSeedInitializer(tenants).run(new DefaultApplicationArguments());

        PlatformTenant hospitalB = tenants.findByKey("hospital-b").orElseThrow();
        assertThat(hospitalB.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(hospitalB.displayName()).isEqualTo("运维改名");
        assertThat(tenants.findByKey("default")).isPresent();
    }

    @Test
    void hospitalBSeedCreatesDoctorBAndIsIdempotent() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        PlatformHospitalBSeedInitializer initializer = new PlatformHospitalBSeedInitializer(
                users, new SaTokenMd5PasswordHasher(new AuthProperties("test-salt")));

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        PlatformUser created = users.findById(PlatformHospitalBSeedInitializer.HOSPITAL_B_USER_ID).orElseThrow();
        assertThat(created.username()).isEqualTo("doctor_b");
        assertThat(created.tenantKey()).isEqualTo("hospital-b");
        assertThat(created.roleKey()).isEqualTo("operator");
    }

    @Test
    void hospitalBSeedKeepsExistingUserUntouched() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(PlatformHospitalBSeedInitializer.HOSPITAL_B_USER_ID,
                "doctor_b", "existing-hash", "viewer", "hospital-b"));

        new PlatformHospitalBSeedInitializer(
                users, new SaTokenMd5PasswordHasher(new AuthProperties("test-salt")))
                .run(new DefaultApplicationArguments());

        assertThat(users.findById(PlatformHospitalBSeedInitializer.HOSPITAL_B_USER_ID)).get()
                .extracting(PlatformUser::roleKey, PlatformUser::passwordHash)
                .containsExactly("viewer", "existing-hash");
    }
}
