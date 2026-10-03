package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.infrastructure.config.AuthProperties;
import com.aicode.core.infrastructure.security.SaTokenMd5PasswordHasher;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 登录审计规范：成功 / 失败都必须留痕，且审计故障不得阻断登录主流程。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAuthAuditTest {

    @Mock
    private AuthTokenPort authTokenPort;

    private InMemoryAuditLogAdapter auditLog;
    private PasswordHasher passwordHasher;
    private PlatformAuthUseCase useCase;

    @BeforeEach
    void setUp() {
        PlatformUserPort users = new InMemoryPlatformUserAdapter();
        auditLog = new InMemoryAuditLogAdapter();
        passwordHasher = new SaTokenMd5PasswordHasher(new AuthProperties("test-salt"));
        users.save(new PlatformUser(2L, "operator", passwordHasher.hash("operator123", "operator"), "operator"));
        useCase = new PlatformAuthUseCase(users, passwordHasher, authTokenPort, auditLog);
    }

    @Test
    void recordsLoginSuccessAuditWithUserIdAndAction() {
        when(authTokenPort.login(2L)).thenReturn("token-abc");

        useCase.login(new PlatformLoginCommand("operator", "operator123"));

        assertThat(auditLog.listAll()).hasSize(1);
        AuditLogEntry entry = auditLog.listAll().get(0);
        assertThat(entry.userId()).isEqualTo(2L);
        assertThat(entry.action()).isEqualTo(AuditAction.LOGIN);
        assertThat(entry.resource()).isEqualTo("platform.auth");
        assertThat(entry.result()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void recordsLoginFailedAuditAndDoesNotLeakPassword() {
        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("operator", "wrong-password")))
                .isInstanceOf(AuthenticationException.class);

        assertThat(auditLog.listAll()).hasSize(1);
        AuditLogEntry entry = auditLog.listAll().get(0);
        assertThat(entry.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(entry.result()).isEqualTo(AuditResult.FAILURE);
        assertThat(entry.userId()).isZero();
        assertThat(entry.resource()).doesNotContain("wrong-password");
        assertThat(entry.detail()).doesNotContain("wrong-password");
    }

    @Test
    void recordsLoginFailedAuditForUnknownUsername() {
        assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("ghost", "whatever")))
                .isInstanceOf(AuthenticationException.class);

        assertThat(auditLog.listAll()).hasSize(1);
        assertThat(auditLog.listAll().get(0).action()).isEqualTo(AuditAction.LOGIN_FAILED);
    }

    @Test
    void auditFailureDoesNotBlockSuccessfulLogin() {
        when(authTokenPort.login(2L)).thenReturn("token-abc");
        InMemoryAuditLogAdapter broken = new InMemoryAuditLogAdapter() {
            @Override
            public void record(AuditLogEntry entry) {
                throw new IllegalStateException("audit store down");
            }
        };
        PlatformAuthUseCase resilient = new PlatformAuthUseCase(
                knownUsers(), passwordHasher, authTokenPort, broken);

        assertThatCode(() -> resilient.login(new PlatformLoginCommand("operator", "operator123")))
                .doesNotThrowAnyException();
    }

    @Test
    void auditEntryFactoryAppliesTimestampAndKeepsTraceId() {
        AuditLogEntry entry = AuditLogEntry.of(1L, AuditAction.EXECUTION_READ, "execution:exec-1",
                AuditResult.DENIED, "trace-9", "blocked");

        assertThat(entry.createdAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(entry.traceId()).isEqualTo("trace-9");
        assertThat(entry.detail()).isEqualTo("blocked");
        assertThat(entry.action()).isEqualTo(AuditAction.EXECUTION_READ);
    }

    @Test
    void loginAuditCarriesTraceIdFromCurrentRequestMdc() {
        // 回归：审计 traceId 必须取自当前请求 MDC（真库验收曾发现审计 traceId 为空）
        MDC.put(TraceIds.MDC_KEY, "trace-from-mdc-1");
        try {
            when(authTokenPort.login(2L)).thenReturn("token-abc");

            useCase.login(new PlatformLoginCommand("operator", "operator123"));
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        assertThat(auditLog.listAll()).hasSize(1);
        assertThat(auditLog.listAll().get(0).traceId()).isEqualTo("trace-from-mdc-1");
    }

    @Test
    void loginFailedAuditCarriesTraceIdFromCurrentRequestMdc() {
        MDC.put(TraceIds.MDC_KEY, "trace-from-mdc-2");
        try {
            assertThatThrownBy(() -> useCase.login(new PlatformLoginCommand("operator", "wrong-password")))
                    .isInstanceOf(AuthenticationException.class);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }

        assertThat(auditLog.listAll()).hasSize(1);
        AuditLogEntry entry = auditLog.listAll().get(0);
        assertThat(entry.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(entry.traceId()).isEqualTo("trace-from-mdc-2");
    }

    private PlatformUserPort knownUsers() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(2L, "operator",
                passwordHasher.hash("operator123", "operator"), "operator"));
        return users;
    }
}
