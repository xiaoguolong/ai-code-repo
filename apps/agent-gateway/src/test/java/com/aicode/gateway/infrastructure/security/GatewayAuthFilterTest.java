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
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关自有用例鉴权过滤器测试（Week 19）。
 *
 * <p>覆盖「拦住了什么」与「放行了什么」两侧：只测拦住会漏掉白名单误伤导致的登录死锁，
 * 只测放行会漏掉未鉴权访问。</p>
 */
class GatewayAuthFilterTest {

    private final FakeSessionStore sessions = new FakeSessionStore();
    private GatewayAuthFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        GatewayAuthSupport support = new GatewayAuthSupport(
                sessions,
                new GatewaySessionProperties("Authorization", 900, "redis"),
                new GatewayProperties("http://platform:8084", null, null),
                new ObjectMapper());
        filter = new GatewayAuthFilter(
                support, new GatewayProperties("http://platform:8084", null, null));
        chain = new RecordingChain();
    }

    @Test
    @DisplayName("登录端点放行（否则「登录需要先登录」死锁）")
    void loginEndpointIsNotBlocked() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/gateway/sessions").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(chain.called).isTrue();
    }

    @Test
    @DisplayName("代理路径不由本过滤器处理（交给 GlobalFilter，避免双重鉴权）")
    void proxyPathIsSkippedByWebFilter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/platform/agents").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(chain.called).isTrue();
    }

    @Test
    @DisplayName("网关业务端点无 token：401 且不进入业务链")
    void missingTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/agents").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("网关业务端点会话失效：401 且不进入业务链")
    void unknownSessionIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer nope")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("网关业务端点会话有效：放行，且请求头原样保留（token 即平台 token）")
    void validSessionPassesWithExchangedCredentials() {
        sessions.put("gw-1", new GatewaySession("platform-token", 1L, "admin", "ADMIN",
                System.currentTimeMillis() / 1000 + 600));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer gw-1")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.called).isTrue();
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer gw-1");
        assertThat(chain.exchange.getAttributes()).containsKey(GatewayAttributes.ATTR_PRINCIPAL);
    }

    /** 记录是否进入业务链、以及进入时的交换对象。 */
    private static final class RecordingChain implements WebFilterChain {

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
