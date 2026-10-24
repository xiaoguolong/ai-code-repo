package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.framework.platform.domain.exception.PlatformRateLimitExceededException;
import com.aicode.framework.platform.domain.port.LoginAttemptPort;
import com.aicode.framework.platform.infrastructure.config.PlatformRateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录限流门面：先限流后认证、失败计数、成功清零、verify 同样受限、关闭开关后完全放行。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAuthenticationFacadeTest {

    private static final String IP = "203.0.113.7";

    @Mock
    private PlatformAuthUseCase authUseCase;

    @Mock
    private LoginAttemptPort attempts;

    private PlatformRateLimitProperties properties;
    private PlatformAuthenticationFacade facade;

    @BeforeEach
    void setUp() {
        properties = new PlatformRateLimitProperties(true, 5, 300, 300, false);
        facade = new PlatformAuthenticationFacade(authUseCase, attempts, properties);
    }

    @Test
    void blockedAttemptIsRejectedBeforeCallingAuth() {
        when(attempts.isBlocked(anyString())).thenReturn(true);

        assertThatThrownBy(() -> facade.login(new PlatformLoginCommand("alice", "pwd"), IP))
                .isInstanceOf(PlatformRateLimitExceededException.class);
        verify(authUseCase, never()).login(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedCredentialRecordsOneAttemptAndRethrows() {
        when(attempts.isBlocked(anyString())).thenReturn(false);
        when(authUseCase.login(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AuthenticationException("invalid credentials"));

        assertThatThrownBy(() -> facade.login(new PlatformLoginCommand("alice", "bad"), IP))
                .isInstanceOf(AuthenticationException.class);
        verify(attempts).recordFailure("203.0.113.7|alice");
    }

    @Test
    void successfulLoginClearsCounter() {
        when(authUseCase.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlatformLoginOutcome("tok", 2L, "alice", "operator", "default"));

        facade.login(new PlatformLoginCommand("alice", "good"), IP);

        verify(attempts).clear("203.0.113.7|alice");
    }

    @Test
    void verifyIsRateLimitedToo() {
        when(attempts.isBlocked(anyString())).thenReturn(true);

        assertThatThrownBy(() -> facade.verify(new PlatformLoginCommand("alice", "pwd"), IP))
                .isInstanceOf(PlatformRateLimitExceededException.class);
        verify(authUseCase, never()).verify(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledSwitchBypassesLimiterEntirely() {
        PlatformRateLimitProperties disabled = new PlatformRateLimitProperties(false, 5, 300, 300, false);
        PlatformAuthenticationFacade open = new PlatformAuthenticationFacade(authUseCase, attempts, disabled);
        when(authUseCase.login(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AuthenticationException("invalid credentials"));

        assertThatThrownBy(() -> open.login(new PlatformLoginCommand("alice", "bad"), IP))
                .isInstanceOf(AuthenticationException.class);
        verify(attempts, never()).isBlocked(anyString());
        verify(attempts, never()).recordFailure(anyString());
    }

    @Test
    void usernameIsNormalizedSoCaseCannotBypassLimits() {
        when(authUseCase.login(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AuthenticationException("invalid credentials"));

        assertThatThrownBy(() -> facade.login(new PlatformLoginCommand("  Alice ", "bad"), IP))
                .isInstanceOf(AuthenticationException.class);
        verify(attempts).recordFailure("203.0.113.7|alice");
    }
}
