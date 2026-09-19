package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.domain.model.User;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.enterprise.domain.port.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 登录用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private UserPort userPort;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuthTokenPort authTokenPort;

    private LoginUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUserUseCase(userPort, passwordHasher, authTokenPort);
    }

    @Test
    void logsInWithValidCredentials() {
        when(userPort.findByUsername("alice")).thenReturn(Optional.of(new User(1L, "alice", "hashed", NOW)));
        when(passwordHasher.matches("pass123", "alice", "hashed")).thenReturn(true);
        when(authTokenPort.login(1L)).thenReturn("token-1");

        LoginUserOutcome outcome = useCase.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.token()).isEqualTo("token-1");
        assertThat(outcome.userId()).isEqualTo(1L);
    }

    @Test
    void rejectsWrongPassword() {
        when(userPort.findByUsername("alice")).thenReturn(Optional.of(new User(1L, "alice", "hashed", NOW)));
        when(passwordHasher.matches("wrong", "alice", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand("alice", "wrong")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsUnknownUser() {
        when(userPort.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.login(new LoginUserCommand("nobody", "pass123")))
                .isInstanceOf(AuthenticationException.class);
    }
}
