package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.GatewayException;
import com.aicode.gateway.domain.exception.GatewayUnauthorizedException;
import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.model.IssuedSession;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.support.FakePlatformClient;
import com.aicode.gateway.support.FakeSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关联会话用例测试（Week 19）。
 */
class GatewaySessionUseCaseTest {

    private FakePlatformClient platform;
    private FakeSessionStore sessions;
    private GatewaySessionUseCase useCase;

    @BeforeEach
    void setUp() {
        platform = new FakePlatformClient();
        sessions = new FakeSessionStore();
        useCase = new GatewaySessionUseCase(platform, sessions, new GatewaySessionProperties("satoken", 60, "redis"));
    }

    @Test
    @DisplayName("登录成功：会话 token 即平台 token（转发时无需换发凭据）")
    void loginUsesPlatformTokenAsSessionToken() {
        StepVerifier.create(useCase.login("admin", "secret"))
                .assertNext(issued -> {
                    // 设计决定：网关联会话 token 就是平台 token，因此客户端拿到的 token
                    // 可以直接透传给平台（不需要网关改写请求头，避免 mutate 派生只读响应）
                    assertThat(issued.sessionToken()).isEqualTo("platform-token-1");
                    assertThat(issued.userId()).isEqualTo(1L);
                    assertThat(issued.username()).isEqualTo("admin");
                    assertThat(issued.roleKey()).isEqualTo("ADMIN");
                    assertThat(issued.expiresInSeconds()).isEqualTo(60L);
                })
                .verifyComplete();

        assertThat(sessions.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("登录成功：会话内容里的平台 token 与 TTL 正确落库")
    void loginStoresPlatformToken() {
        IssuedSession issued = useCase.login("admin", "secret").block();
        assertThat(issued).isNotNull();
        GatewaySession stored = sessions.find(issued.sessionToken()).block();
        assertThat(stored).isNotNull();
        assertThat(stored.platformToken()).isEqualTo("platform-token-1");
        assertThat(stored.expiredAt(System.currentTimeMillis() / 1000)).isFalse();
    }

    @Test
    @DisplayName("空用户名 / 空密码：400 VALIDATION_ERROR，不打上游")
    void loginRejectsBlankCredentials() {
        StepVerifier.create(useCase.login("  ", "secret"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayException.class);
                    assertThat(((GatewayException) error).errorCode()).isEqualTo(GatewayErrorCode.VALIDATION_ERROR);
                })
                .verify();
        StepVerifier.create(useCase.login("admin", ""))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(GatewayException.class))
                .verify();
        assertThat(sessions.size()).isZero();
    }

    @Test
    @DisplayName("平台 401：错误码与状态原样透传（4xx 不收敛为 502）")
    void loginPropagatesPlatform4xx() {
        platform.loginFailure = new PlatformBusinessException("UNAUTHORIZED", 401, "账号或密码错误");

        StepVerifier.create(useCase.login("admin", "wrong"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(PlatformBusinessException.class);
                    assertThat(((PlatformBusinessException) error).upstreamStatus()).isEqualTo(401);
                    assertThat(((PlatformBusinessException) error).errorCode()).isEqualTo(GatewayErrorCode.UNAUTHORIZED);
                })
                .verify();
        assertThat(sessions.size()).isZero();
    }

    @Test
    @DisplayName("平台不可达：502 GATEWAY_UPSTREAM_ERROR")
    void loginMapsUnreachableToBadGateway() {
        platform.loginFailure = FakePlatformClient.unreachable();

        StepVerifier.create(useCase.login("admin", "secret"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayUpstreamException.class);
                    assertThat(((GatewayUpstreamException) error).errorCode())
                            .isEqualTo(GatewayErrorCode.GATEWAY_UPSTREAM_ERROR);
                })
                .verify();
    }

    @Test
    @DisplayName("平台 5xx：收敛为 502（不把上游 500 透传给客户端）")
    void loginMapsPlatform5xxToBadGateway() {
        platform.loginFailure = new GatewayUpstreamException(
                GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, "平台返回 500", "INTERNAL_ERROR", 500);

        StepVerifier.create(useCase.login("admin", "secret"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayUpstreamException.class);
                    assertThat(((GatewayUpstreamException) error).errorCode())
                            .isEqualTo(GatewayErrorCode.UPSTREAM_SERVER_ERROR);
                })
                .verify();
    }

    @Test
    @DisplayName("会话存储不可用：异常向上抛（不能把「查不出来」当「没登录」静默放行）")
    void loginFailsWhenStoreUnavailable() {
        sessions.failing = true;
        StepVerifier.create(useCase.login("admin", "secret")).expectError().verify();
    }

    @Test
    @DisplayName("登出：删除会话；空 token 抛未授权")
    void logoutRemovesSession() {
        IssuedSession issued = useCase.login("admin", "secret").block();
        assertThat(issued).isNotNull();

        StepVerifier.create(useCase.logout(issued.sessionToken())).verifyComplete();
        assertThat(sessions.size()).isZero();

        StepVerifier.create(useCase.logout("  "))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(GatewayUnauthorizedException.class))
                .verify();
    }
}
