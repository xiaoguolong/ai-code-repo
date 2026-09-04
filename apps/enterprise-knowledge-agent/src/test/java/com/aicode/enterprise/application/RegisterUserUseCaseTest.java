package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.port.PasswordHasher;
import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.User;
import com.aicode.enterprise.domain.port.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 注册用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private UserPort userPort;

    @Mock
    private PasswordHasher passwordHasher;

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(userPort, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersUserWithHashedPassword() {
        when(passwordHasher.hash("pass123", "alice")).thenReturn("hashed");
        when(userPort.create(any())).thenReturn(new User(1L, "alice", "hashed", NOW));

        RegisterUserOutcome outcome = useCase.register(new RegisterUserCommand("alice", "pass123"));

        assertThat(outcome.userId()).isEqualTo(1L);
        assertThat(outcome.username()).isEqualTo("alice");
    }

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand("  ", "pass123")))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void rejectsDuplicateUsername() {
        when(userPort.findByUsername("alice")).thenReturn(Optional.of(new User(1L, "alice", "h", NOW)));

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand("alice", "pass123")))
                .isInstanceOf(ConflictException.class);
    }
}
