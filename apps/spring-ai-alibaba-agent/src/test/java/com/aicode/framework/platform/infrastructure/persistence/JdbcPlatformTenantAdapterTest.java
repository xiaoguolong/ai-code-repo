package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户落库适配器（H2 MODE=PostgreSQL + Flyway V4）。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcPlatformTenantAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-24T00:00:00Z");

    @Autowired
    private PlatformTenantPort tenantPort;

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(tenantPort).isInstanceOf(JdbcPlatformTenantAdapter.class);
    }

    @Test
    void savesFindsAndUpdatesTenant() {
        tenantPort.save(new PlatformTenant("jdbc-tenant", "落库租户", TenantStatus.ACTIVE, CREATED_AT));

        assertThat(tenantPort.findByKey("jdbc-tenant")).get()
                .extracting(PlatformTenant::displayName).isEqualTo("落库租户");

        tenantPort.save(new PlatformTenant("jdbc-tenant", "落库租户（停用）", TenantStatus.SUSPENDED, CREATED_AT));

        assertThat(tenantPort.findByKey("jdbc-tenant")).get()
                .extracting(PlatformTenant::status).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenantPort.isActive("jdbc-tenant")).isFalse();
    }

    @Test
    void returnsEmptyAndInactiveForUnknownTenant() {
        assertThat(tenantPort.findByKey("no-such-tenant")).isEmpty();
        assertThat(tenantPort.isActive("no-such-tenant")).isFalse();
    }

    @Test
    void seedProvidesDefaultAndSecondTenantForIsolation() {
        assertThat(tenantPort.findByKey("default")).isPresent();
        assertThat(tenantPort.findByKey("hospital-b")).isPresent();
    }
}
