package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.infrastructure.config.AuthProperties;
import com.aicode.core.infrastructure.security.SaTokenMd5PasswordHasher;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformTenantAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 认证用例的租户语义：登录/校验都带租户，停用租户 401 且不泄露原因。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAuthTenantTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-24T00:00:00Z");

    @Mock
    private AuthTokenPort authTokenPort;

    private InMemoryPlatformUserAdapter users;
    private InMemoryPlatformTenantAdapter tenants;
    private InMemoryAuditLogAdapter auditLog;
    private PasswordHasher passwordHasher;
    private PlatformAuthUseCase useCase;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        tenants = new InMemoryPlatformTenantAdapter();
        auditLog = new InMemoryAuditLogAdapter();
        passwordHasher = new SaTokenMd5PasswordHasher(new AuthProperties("test-salt"));
        useCase = new PlatformAuthUseCase(users, passwordHasher, authTokenPort, auditLog, tenants);

        tenants.save(new PlatformTenant("default", "默认租户", TenantStatus.ACTIVE, CREATED_AT));
        tenants.save(new PlatformTenant("hospital-b", "演示医院B", TenantStatus.SUSPENDED, CREATED_AT));
        users.save(new PlatformUser(2L, "operator", passwordHasher.hash("operator123", "operator"),
                "operator", "default"));
        users.save(new PlatformUser(4L, "doctor-b", passwordHasher.hash("doctor123", "doctor-b"),
                "operator", "hospital-b"));
    }

    @Test
    void verifyReturnsIdentityWithTenantWithoutOpeningSession() {
        PlatformUser user = useCase.verify(new PlatformLoginCommand("operator", "operator123"));

        assertThat(user.id()).isEqualTo(2L);
        assertThat(user.tenantKey()).isEqualTo("default");
        assertThat(auditLog.listAll()).extracting(AuditLogEntry::action).containsExactly(AuditAction.LOGIN);
        assertThat(auditLog.listAll().get(0).detail()).contains("sso=true");
    }

    @Test
    void verifyRejectsWrongPasswordWithoutAuditLeak() {
        assertThatThrownBy(() -> useCase.verify(new PlatformLoginCommand("operator", "wrong")))
                .isInstanceOf(AuthenticationException.class);

        assertThat(auditLog.listAll()).extracting(AuditLogEntry::action).containsExactly(AuditAction.LOGIN_FAILED);
        assertThat(auditLog.listAll().get(0).detail()).doesNotContain("wrong");
    }

    @Test
    void suspendedTenantCannotVerifyNorLogin() {
        assertThatThrownBy(() -> useCase.verify(new PlatformLoginCommand("doctor-b", "doctor123")))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("doctor-b", "doctor123")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void loginReturnsTenantKeyForActiveTenant() {
        when(authTokenPort.login(2L)).thenReturn("token-tenant");

        PlatformLoginOutcome outcome = useCase.login(new PlatformLoginCommand("operator", "operator123"));

        assertThat(outcome.token()).isEqualTo("token-tenant");
        assertThat(outcome.tenantKey()).isEqualTo("default");
    }
}
