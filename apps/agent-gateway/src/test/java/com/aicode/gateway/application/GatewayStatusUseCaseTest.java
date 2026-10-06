package com.aicode.gateway.application;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.model.GatewayStatusView;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.observability.SkyWalkingAttachmentProbe;
import com.aicode.gateway.support.FakeCacheStore;
import com.aicode.gateway.support.FakePlatformClient;
import com.aicode.gateway.support.FakeSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关自检用例测试（Week 19）。
 *
 * <p>自检必须在「依赖坏掉」时也能给出答案：Redis 挂了要知道是「0 个会话」而不是 500，
 * 否则自检在故障时最没用（这与「会话查不出来必须报错」是两回事，前者是概况统计）。</p>
 */
class GatewayStatusUseCaseTest {

    private final FakeSessionStore sessions = new FakeSessionStore();
    private final FakeCacheStore cache = new FakeCacheStore();
    private final FakePlatformClient platform = new FakePlatformClient();
    private final GatewayStatusUseCase useCase = new GatewayStatusUseCase(
            new GatewayProperties("http://platform:8084/", "/api/v1/platform/", "api/v1/gateway"),
            new GatewaySessionProperties("satoken", 900, "redis"),
            new GatewayCatalogProperties(45, "gw:catalog:agents", 300),
            sessions,
            cache,
            platform,
            new SkyWalkingAttachmentProbe());

    @Test
    @DisplayName("自检视图：路由前缀归一、会话与缓存计数、链路上下文完整")
    void statusAggregatesEverything() {
        sessions.put("t1", new GatewaySession("p1", 1L, "admin", "ADMIN", 0L));
        cache.putDirect("gw:catalog:agents:v1", "[]");

        StepVerifier.create(useCase.status("trace-1", "00-abc-def-01", "X-Trace-Id", "trace-1"))
                .assertNext(view -> {
                    assertThat(view.application()).isEqualTo("agent-gateway");
                    assertThat(view.routes().platformUri()).isEqualTo("http://platform:8084");
                    assertThat(view.routes().platformProxyPath()).isEqualTo("/api/v1/platform");
                    assertThat(view.routes().gatewayApiPath()).isEqualTo("/api/v1/gateway");
                    assertThat(view.sessions().store()).isEqualTo("redis");
                    assertThat(view.sessions().activeSessions()).isEqualTo(1);
                    assertThat(view.sessions().ttlSeconds()).isEqualTo(900);
                    assertThat(view.sessions().catalogCached()).isEqualTo(1);
                    assertThat(view.sessions().catalogTtlSeconds()).isEqualTo(45);
                    assertThat(view.tracing().traceId()).isEqualTo("trace-1");
                    assertThat(view.tracing().traceparent()).isEqualTo("00-abc-def-01");
                    assertThat(view.tracing().headerName()).isEqualTo("X-Trace-Id");
                    assertThat(view.tracing().inboundTraceId()).isEqualTo("trace-1");
                    // 单测环境没有挂 Agent：探针必须如实回答 false
                    assertThat(view.skywalkingAgent()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("依赖故障时自检仍返回视图：计数按 0 处理，不抛异常")
    void statusToleratesBrokenDependencies() {
        sessions.failing = true;
        cache.failing = true;

        StepVerifier.create(useCase.status("trace-2", "", "X-Trace-Id", ""))
                .assertNext(view -> {
                    assertThat(view.sessions().activeSessions()).isZero();
                    assertThat(view.sessions().catalogCached()).isZero();
                    assertThat(view.tracing().traceId()).isEqualTo("trace-2");
                    assertThat(view.tracing().traceparent()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("null 入参不产生 null 字段（响应体不出现 null 头字段）")
    void statusNormalizesNulls() {
        StepVerifier.create(useCase.status(null, null, null, null))
                .assertNext(view -> {
                    assertThat(view.tracing().traceId()).isEmpty();
                    assertThat(view.tracing().traceparent()).isEmpty();
                    assertThat(view.tracing().headerName()).isEmpty();
                    assertThat(view.tracing().inboundTraceId()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("平台地址来自出站端口（自检展示的是真正会请求的地址）")
    void statusExposesPlatformBaseUri() {
        assertThat(useCase.platformBaseUri()).isEqualTo("http://fake-platform:8084");
    }
}
