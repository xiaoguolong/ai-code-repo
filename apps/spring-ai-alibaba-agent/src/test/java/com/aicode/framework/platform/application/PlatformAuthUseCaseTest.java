package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.infrastructure.config.AuthProperties;
import com.aicode.core.infrastructure.security.SaTokenMd5PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 平台认证用例测试。 */
@ExtendWith(MockitoExtension.class)
class PlatformAuthUseCaseTest {

    @Mock
    private AuthTokenPort authTokenPort;

    private InMemoryPlatformUserAdapter users;
    private InMemoryAuditLogAdapter audit;
    private PasswordHasher passwordHasher;
    private PlatformAuthUseCase useCase;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        audit = new InMemoryAuditLogAdapter();
        passwordHasher = new SaTokenMd5PasswordHasher(new AuthProperties("test-salt"));
        useCase = new PlatformAuthUseCase(users, passwordHasher, authTokenPort, audit);

        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("patient-risk"), Set.of("PatientLookupTool"), Set.of("P001")));

        String hash = passwordHasher.hash("operator123", "operator");
        users.save(new PlatformUser(2L, "operator", hash, "operator"));
    }

    @Test
    void loginReturnsTokenAndRole() {
        when(authTokenPort.login(2L)).thenReturn("token-abc");

        PlatformLoginOutcome outcome = useCase.login(new PlatformLoginCommand("operator", "operator123"));

        assertThat(outcome.token()).isEqualTo("token-abc");
        assertThat(outcome.roleKey()).isEqualTo("operator");
        verify(authTokenPort).login(2L);
    }

    @Test
    void rejectsInvalidPassword() {
        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("operator", "wrong")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsDisabledUserOnLoginAndVerifyWithoutLeakingState() {
        users.updateDisabledAt(2L, Instant.parse("2026-10-25T07:30:00Z"));

        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("operator", "operator123")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("invalid credentials");
        assertThatThrownBy(() -> useCase.verify(new PlatformLoginCommand("operator", "operator123")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("invalid credentials");
        assertThat(audit.listAll())
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.action().name()).isEqualTo("LOGIN_FAILED");
                    assertThat(entry.detail()).contains("disabled=true");
                });
    }

    @Test
    void meRejectsDisabledUser() {
        users.updateDisabledAt(2L, Instant.parse("2026-10-25T07:30:00Z"));

        assertThatThrownBy(() -> useCase.me(2L))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void seedStyleSaveDoesNotReenableDisabledUser() {
        users.updateDisabledAt(2L, Instant.parse("2026-10-25T07:30:00Z"));
        users.save(new PlatformUser(2L, "operator", users.findById(2L).orElseThrow().passwordHash(), "operator"));

        assertThat(users.findById(2L).orElseThrow().disabled()).isTrue();
        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("operator", "operator123")))
                .isInstanceOf(AuthenticationException.class);
    }
}
