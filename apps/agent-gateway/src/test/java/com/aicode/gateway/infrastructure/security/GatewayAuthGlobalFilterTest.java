package com.aicode.gateway.infrastructure.security;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import com.aicode.gateway.support.FakeSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代理路由鉴权全局过滤器测试（Week 19）。
 *
 * <p><b>本测试是本周安防红线的守卫</b>：Spring Cloud Gateway 的路由请求不经过
 * {@code WebFilter}，如果只有 WebFilter 做鉴权，透传路径会完全裸奔。
 * 第一条断言（缺 token → 401）就是防止将来有人「顺手删掉」这个 GlobalFilter。</p>
 */
class GatewayAuthGlobalFilterTest {

    private final FakeSessionStore sessions = new FakeSessionStore();
    private GatewayAuthGlobalFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        GatewayAuthSupport support = new GatewayAuthSupport(
                sessions,
                new GatewaySessionProperties("Authorization", 900, "redis"),
                new GatewayProperties("http://platform:8084", null, null),
                new ObjectMapper());
        filter = new GatewayAuthGlobalFilter(support);
        chain = new RecordingChain();
    }

    @Test
    @DisplayName("优先级最高（必须早于路由过滤器发出上游请求）")
    void runsBeforeRoutingFilters() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("代理路径缺 token：401 且不发往上游")
    void proxyWithoutTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/platform/agents").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("代理路径会话有效：放行、身份入属性、请求头原样透传（token 即平台 token）")
    void proxyWithValidSessionIsForwarded() {
        sessions.put("gw-proxy", new GatewaySession("platform-token", 2L, "admin", "ADMIN",
                System.currentTimeMillis() / 1000 + 600));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/platform/agents/medical-assistant/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer gw-proxy")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isTrue();
        // 不改写请求头是关键：改写只能靠 exchange.mutate()，而它派生对象的响应在真实 Netty 下只读，
        // 代理写出上游响应时会抛 UnsupportedOperationException（现象：平台侧 200、客户端连接被关闭，实测踩过）
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer gw-proxy");
        assertThat(chain.exchange.getAttributes()).containsKey(GatewayAttributes.ATTR_PRINCIPAL);
    }

    @Test
    @DisplayName("白名单路径直接放行（探活不需要会话）")
    void whitelistedPathPassesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isTrue();
    }

    /** 记录是否进入下游链。 */
    private static final class RecordingChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {

        private boolean called;
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
