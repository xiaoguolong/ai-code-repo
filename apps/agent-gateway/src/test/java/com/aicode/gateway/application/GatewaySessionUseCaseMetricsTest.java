package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.support.FakePlatformClient;
import com.aicode.gateway.support.FakeSessionStore;
import com.aicode.gateway.support.RecordingGatewayMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关登录用例的指标上报测试（Week 20）。
 *
 * <p>登录失败率是网关最敏感的指标（把平台不可达或密钥错配第一时间暴露出来），
 * 因此成功与失败两条路径都必须打点，且失败时业务语义（401/502）不变。</p>
 */
class GatewaySessionUseCaseMetricsTest {

    private final FakePlatformClient platform = new FakePlatformClient();
    private final FakeSessionStore sessions = new FakeSessionStore();
    private final RecordingGatewayMetrics metrics = new RecordingGatewayMetrics();

    private GatewaySessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GatewaySessionUseCase(
                platform, sessions, new GatewaySessionProperties("Authorization", 600, "redis"), metrics);
    }

    @Test
    @DisplayName("登录成功 → login:SUCCESS")
    void recordsLoginSuccess() {
        StepVerifier.create(useCase.login("admin", "admin123"))
                .expectNextCount(1).verifyComplete();

        assertThat(metrics.calls).containsExactly("login:SUCCESS");
    }

    @Test
    @DisplayName("平台拒绝凭据 → login:FAILURE，错误语义保持 401 透传")
    void recordsLoginFailure() {
        platform.loginFailure = new PlatformBusinessException("UNAUTHORIZED", 401, "登录失败");

        StepVerifier.create(useCase.login("admin", "wrong"))
                .expectError(PlatformBusinessException.class)
                .verify();

        assertThat(metrics.calls).containsExactly("login:FAILURE");
    }

    @Test
    @DisplayName("入参为空 → login:FAILURE（客户端错误同样计入失败率，但不打平台）")
    void blankCredentialsCountAsFailure() {
        StepVerifier.create(useCase.login("", ""))
                .expectError()
                .verify();

        assertThat(metrics.calls).containsExactly("login:FAILURE");
    }
}
