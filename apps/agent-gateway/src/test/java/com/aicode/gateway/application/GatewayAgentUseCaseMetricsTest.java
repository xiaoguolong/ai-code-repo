package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.aicode.gateway.support.FakeCacheStore;
import com.aicode.gateway.support.FakePlatformClient;
import com.aicode.gateway.support.RecordingGatewayMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关目录/执行用例的指标上报测试（Week 20）。
 *
 * <p>指标要回答运维最常问的三件事：缓存命中率、Agent 执行成功率、上游失败原因。
 * 因此这里分别断言三个分支，且验证「指标失败不影响业务」由适配器吞异常保证。</p>
 */
class GatewayAgentUseCaseMetricsTest {

    private final FakePlatformClient platform = new FakePlatformClient();
    private final FakeCacheStore cache = new FakeCacheStore();
    private final RecordingGatewayMetrics metrics = new RecordingGatewayMetrics();
    private final SessionPrincipal principal = new SessionPrincipal("session-1", "platform-token-1", 1L, "admin");

    private GatewayAgentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GatewayAgentUseCase(
                platform, cache, new GatewayCatalogProperties(30, "gw:catalog:agents", 300),
                new ObjectMapper(), metrics);
    }

    @Test
    @DisplayName("目录未命中 → catalog:miss；命中 → catalog:hit，且命中时不再回源")
    void recordsCatalogCacheHitRate() {
        StepVerifier.create(useCase.listAgents(principal, "t1", false))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.listAgents(principal, "t1", false))
                .expectNextCount(1).verifyComplete();

        assertThat(platform.listCount).isEqualTo(1);
        assertThat(metrics.calls).containsExactly("catalog:miss", "catalog:hit");
    }

    @Test
    @DisplayName("强制刷新 → catalog:miss（跳过缓存语义不能被指标掩盖）")
    void refreshCountsAsMiss() {
        StepVerifier.create(useCase.listAgents(principal, "t1", true))
                .expectNextCount(1).verifyComplete();

        assertThat(metrics.calls).containsExactly("catalog:miss");
        assertThat(platform.listCount).isEqualTo(1);
    }

    @Test
    @DisplayName("执行成功 → run:SUCCESS")
    void recordsRunSuccess() {
        StepVerifier.create(useCase.runAgent(principal, "t1", "medical-assistant", Map.of("patientId", "P001")))
                .expectNextCount(1).verifyComplete();

        assertThat(metrics.saw("run:SUCCESS")).isTrue();
        assertThat(metrics.saw("upstream:")).isFalse();
    }

    @Test
    @DisplayName("执行失败 → run:FAILURE + 上游错误码（5xx 收敛为 UPSTREAM_SERVER_ERROR）")
    void recordsRunFailureAndUpstreamCode() {
        platform.runFailure = new GatewayUpstreamException(
                com.aicode.gateway.dto.GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, "平台 503", "UPSTREAM", 503);

        StepVerifier.create(useCase.runAgent(principal, "t1", "medical-assistant", Map.of()))
                .expectError(GatewayUpstreamException.class)
                .verify();

        assertThat(metrics.saw("run:FAILURE")).isTrue();
        assertThat(metrics.calls).contains("upstream:UPSTREAM_SERVER_ERROR");
    }

    @Test
    @DisplayName("入参非法（agentKey 空）→ 不算一次执行，也不打上游")
    void validationFailureIsNotCountedAsRun() {
        StepVerifier.create(useCase.runAgent(principal, "t1", " ", Map.of()))
                .expectError(GatewayUpstreamException.class)
                .verify();

        assertThat(platform.runCount).isZero();
        assertThat(metrics.calls).isEmpty();
    }
}
