package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.model.User;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.infrastructure.config.AuthProperties;
import com.aicode.core.infrastructure.security.SaTokenMd5PasswordHasher;
import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.model.PlatformIdentity;
import com.aicode.enterprise.domain.port.PlatformIdentityPort;
import com.aicode.enterprise.infrastructure.sso.SsoProperties;
import com.aicode.enterprise.support.InMemorySsoUserLinkAdapter;
import com.aicode.enterprise.support.InMemoryUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSO 登录用例：首次开户、复用建链、同名冲突、关闭自动开户、身份源失败。
 */
class SsoLoginUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    private InMemoryUserPort userPort;
    private InMemorySsoUserLinkAdapter linkPort;
    private PasswordHasher passwordHasher;
    private AuthTokenPort authTokenPort;
    private SsoProperties properties;

    @BeforeEach
    void setUp() {
        userPort = new InMemoryUserPort();
        linkPort = new InMemorySsoUserLinkAdapter();
        passwordHasher = new SaTokenMd5PasswordHasher(new AuthProperties("test-salt"));
        authTokenPort = new AuthTokenPort() {
            @Override
            public String login(long userId) {
                return "enterprise-token-" + userId;
            }

            @Override
            public void logout() {
                // no-op
            }
        };
        properties = new SsoProperties(true, "http://localhost:8084", 3000, true);
    }

    @Test
    void firstLoginProvisionsLocalUserAndLinkThenIssuesLocalToken() {
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        LoginUserOutcome outcome = useCase.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.username()).isEqualTo("alice");
        assertThat(outcome.userId()).isNotNull();
        assertThat(outcome.token()).isEqualTo("enterprise-token-" + outcome.userId());
        assertThat(linkPort.findEnterpriseUserId(42L)).contains(outcome.userId());
        assertThat(linkPort.size()).isEqualTo(1);

        User created = userPort.findById(outcome.userId()).orElseThrow();
        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.passwordHash()).isNotEqualTo("pass123");
        assertThat(passwordHasher.matches("pass123", "alice", created.passwordHash())).isFalse();
    }

    @Test
    void secondLoginReusesExistingLinkAndDoesNotProvisionAgain() {
        User local = userPort.save(new User(null, "alice", "hash", NOW));
        linkPort.link(42L, local.id(), "default");
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        LoginUserOutcome outcome = useCase.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.userId()).isEqualTo(local.id());
        assertThat(linkPort.size()).isEqualTo(1);
    }

    @Test
    void conflictWhenLocalUsernameExistsWithoutLink() {
        userPort.save(new User(null, "alice", "hash", NOW));
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand("alice", "pass123")))
                .isInstanceOf(ConflictException.class);
        assertThat(linkPort.size()).isZero();
    }

    @Test
    void rejectsUnknownIdentityWhenAutoProvisionDisabled() {
        properties = new SsoProperties(true, "http://localhost:8084", 3000, false);
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand("alice", "pass123")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(userPort.findByUsername("alice")).isEmpty();
    }

    @Test
    void propagatesIdentityFailureWithoutProvisioning() {
        PlatformIdentityPort failing = (username, password) -> {
            throw new AuthenticationException("invalid credentials");
        };
        SsoLoginUseCase useCase = new SsoLoginUseCase(
                failing, linkPort, userPort, passwordHasher, authTokenPort, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand("alice", "bad")))
                .isInstanceOf(AuthenticationException.class);
        assertThat(userPort.findByUsername("alice")).isEmpty();
    }

    @Test
    void unlinkAndDeleteLocalUserAllowsReprovisioning() {
        User local = userPort.save(new User(null, "alice", "hash", NOW));
        linkPort.link(42L, local.id(), "default");
        // 运维语义：只解绑 + 删本地用户后，下次 SSO 登录应重新开户，而不是撞 409 死结。
        linkPort.unlink(42L);
        userPort.deleteById(local.id());
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        LoginUserOutcome outcome = useCase.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.userId()).isNotEqualTo(local.id());
        assertThat(userPort.findById(local.id())).isEmpty();
        assertThat(linkPort.findEnterpriseUserId(42L)).contains(outcome.userId());
    }

    @Test
    void rejectsBlankCredentialsBeforeCallingPlatform() {
        SsoLoginUseCase useCase = useCase(identity(42L, "alice", "default"));

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand(" ", "pass123")))
                .isInstanceOf(com.aicode.core.domain.exception.InvalidChatRequestException.class);
    }

    private SsoLoginUseCase useCase(PlatformIdentity identity) {
        return new SsoLoginUseCase((username, password) -> identity,
                linkPort, userPort, passwordHasher, authTokenPort, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PlatformIdentity identity(long platformUserId, String username, String tenantKey) {
        return new PlatformIdentity(platformUserId, username, "operator", tenantKey);
    }
}
