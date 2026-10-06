package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.aicode.gateway.support.FakeCacheStore;
import com.aicode.gateway.support.FakePlatformClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 目录与执行用例测试（Week 19）。
 *
 * <p>本周最关键的三条行为都在这里锁死：</p>
 * <ol>
 *   <li>缓存命中<b>不再</b>回源（否则缓存等于没有）；</li>
 *   <li>缓存故障只降级为未命中（Redis 不是单点）；</li>
 *   <li>平台 4xx 透传业务码、5xx 收敛 502。</li>
 * </ol>
 */
class GatewayAgentUseCaseTest {

    private static final SessionPrincipal PRINCIPAL =
            new SessionPrincipal("gw-session", "platform-token", 1L, "admin");

    private FakePlatformClient platform;
    private FakeCacheStore cache;
    private GatewayAgentUseCase useCase;

    @BeforeEach
    void setUp() {
        platform = new FakePlatformClient();
        cache = new FakeCacheStore();
        useCase = new GatewayAgentUseCase(
                platform, cache, new GatewayCatalogProperties(30, "gw:catalog:agents", 300), new ObjectMapper());
    }

    @Test
    @DisplayName("首次查询：回源平台、回填缓存，且带上平台 token 与 traceId")
    void firstQueryHitsPlatformAndCaches() {
        StepVerifier.create(useCase.listAgents(PRINCIPAL, "trace-1", false))
                .assertNext(agents -> {
                    assertThat(agents).hasSize(1);
                    assertThat(agents.get(0).agentKey()).isEqualTo("medical-assistant");
                })
                .verifyComplete();

        assertThat(platform.listCount).isEqualTo(1);
        assertThat(platform.lastToken).isEqualTo("platform-token");
        assertThat(platform.lastTraceId).isEqualTo("trace-1");
        assertThat(cache.putCount).isEqualTo(1);
        assertThat(cache.lastTtlSeconds).isEqualTo(30L);
    }

    @Test
    @DisplayName("二次查询：命中缓存，不再回源")
    void secondQueryUsesCache() {
        useCase.listAgents(PRINCIPAL, "trace-1", false).block();
        List<AgentSummary> second = useCase.listAgents(PRINCIPAL, "trace-1", false).block();

        assertThat(second).hasSize(1);
        assertThat(platform.listCount).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh=true：跳过缓存强制回源")
    void refreshBypassesCache() {
        useCase.listAgents(PRINCIPAL, "trace-1", false).block();
        useCase.listAgents(PRINCIPAL, "trace-1", true).block();

        assertThat(platform.listCount).isEqualTo(2);
    }

    @Test
    @DisplayName("缓存内容损坏：视为未命中并回源（不报错、不返回空）")
    void brokenCacheFallsBackToPlatform() {
        cache.putDirect(useCase.cacheKey(), "{not-json");

        StepVerifier.create(useCase.listAgents(PRINCIPAL, "trace-1", false))
                .assertNext(agents -> assertThat(agents).hasSize(1))
                .verifyComplete();
        assertThat(platform.listCount).isEqualTo(1);
    }

    @Test
    @DisplayName("缓存全挂：读取与回填都失败，业务仍然成功（缓存不是单点）")
    void cacheFailureDoesNotBreakBusiness() {
        cache.failing = true;

        StepVerifier.create(useCase.listAgents(PRINCIPAL, "trace-1", false))
                .assertNext(agents -> assertThat(agents).hasSize(1))
                .verifyComplete();
        assertThat(platform.listCount).isEqualTo(1);
    }

    @Test
    @DisplayName("平台不可达：502 GATEWAY_UPSTREAM_ERROR")
    void platformUnreachableMapsToBadGateway() {
        platform.listFailure = FakePlatformClient.unreachable();

        StepVerifier.create(useCase.listAgents(PRINCIPAL, "trace-1", false))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayUpstreamException.class);
                    assertThat(((GatewayUpstreamException) error).errorCode())
                            .isEqualTo(GatewayErrorCode.GATEWAY_UPSTREAM_ERROR);
                })
                .verify();
    }

    @Test
    @DisplayName("执行：透传入参、写幂等键并返回平台结果")
    void runAgentPassesThroughInputAndResult() {
        StepVerifier.create(useCase.runAgent(PRINCIPAL, "trace-9", "medical-assistant", java.util.Map.of("patientId", "P001")))
                .assertNext(result -> assertThat(result)
                        .containsEntry("executionId", "exec-1")
                        .containsEntry("status", "COMPLETED"))
                .verifyComplete();

        assertThat(platform.runCount).isEqualTo(1);
        assertThat(platform.lastAgentKey).isEqualTo("medical-assistant");
        assertThat(platform.lastInput).containsEntry("patientId", "P001");
        assertThat(cache.putIfAbsentCount).isEqualTo(1);
        assertThat(cache.lastTtlSeconds).isEqualTo(300L);
    }

    @Test
    @DisplayName("执行：空 agentKey 直接 400，不打上游")
    void runAgentRejectsBlankKey() {
        StepVerifier.create(useCase.runAgent(PRINCIPAL, "trace-9", "  ", java.util.Map.of()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayUpstreamException.class);
                    assertThat(((GatewayUpstreamException) error).errorCode())
                            .isEqualTo(GatewayErrorCode.VALIDATION_ERROR);
                })
                .verify();
        assertThat(platform.runCount).isZero();
    }

    @Test
    @DisplayName("执行：平台 4xx 透传、5xx 收敛 502")
    void runAgentMapsUpstreamFailures() {
        platform.runFailure = new PlatformBusinessException("FORBIDDEN", 403, "无权执行");
        StepVerifier.create(useCase.runAgent(PRINCIPAL, "t", "agent", java.util.Map.of()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(PlatformBusinessException.class);
                    assertThat(((PlatformBusinessException) error).errorCode()).isEqualTo(GatewayErrorCode.FORBIDDEN);
                })
                .verify();

        platform.runFailure = new GatewayUpstreamException(
                GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, "平台返回 503", "INTERNAL_ERROR", 503);
        StepVerifier.create(useCase.runAgent(PRINCIPAL, "t", "agent", java.util.Map.of()))
                .expectErrorSatisfies(error -> assertThat(((GatewayUpstreamException) error).errorCode())
                        .isEqualTo(GatewayErrorCode.UPSTREAM_SERVER_ERROR))
                .verify();
    }

    @Test
    @DisplayName("执行：幂等键写入失败不影响业务")
    void runAgentIgnoresIdempotencyFailure() {
        cache.failing = true;
        StepVerifier.create(useCase.runAgent(PRINCIPAL, "t", "agent", java.util.Map.of()))
                .assertNext(result -> assertThat(result).containsEntry("status", "COMPLETED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("缓存键带版本后缀（便于将来换结构时灰度）")
    void cacheKeyIsVersioned() {
        assertThat(useCase.cacheKey()).isEqualTo("gw:catalog:agents:v1");
    }
}
