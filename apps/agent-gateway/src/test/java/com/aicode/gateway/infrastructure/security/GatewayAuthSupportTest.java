package com.aicode.gateway.infrastructure.security;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.dto.GatewayErrorCode;
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
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关鉴权支撑测试（Week 19）。
 *
 * <p>本类是鉴权语义的<b>唯一</b>实现点（WebFilter 与 GlobalFilter 都调它），
 * 因此这里覆盖 token 提取、白名单、过期与凭据换发四件事。</p>
 */
class GatewayAuthSupportTest {

    private final FakeSessionStore sessions = new FakeSessionStore();
    private final GatewaySessionProperties sessionProperties = new GatewaySessionProperties("satoken", 900, "redis");
    private final GatewayProperties routes = new GatewayProperties("http://platform:8084", null, null);
    private GatewayAuthSupport support;

    @BeforeEach
    void setUp() {
        support = new GatewayAuthSupport(sessions, sessionProperties, routes, new ObjectMapper());
    }

    @Test
    @DisplayName("白名单：登录端点、探活、错误页放行；业务端点不放行")
    void whitelistCoversLoginAndProbes() {
        assertThat(support.isWhitelisted("/api/v1/gateway/sessions")).isTrue();
        assertThat(support.isWhitelisted("/actuator/health")).isTrue();
        assertThat(support.isWhitelisted("/actuator/health/readiness")).isTrue();
        assertThat(support.isWhitelisted("/actuator/info")).isTrue();
        assertThat(support.isWhitelisted("/error")).isTrue();

        assertThat(support.isWhitelisted("/api/v1/gateway/agents")).isFalse();
        assertThat(support.isWhitelisted("/api/v1/platform/agents/medical-assistant/runs")).isFalse();
        assertThat(support.isWhitelisted(null)).isFalse();
    }

    @Test
    @DisplayName("取 token：Bearer 优先，其次自定义头；空值返回空串")
    void extractTokenPrefersBearer() {
        MockServerHttpRequest bearer = MockServerHttpRequest.get("/api/v1/gateway/agents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer gw-token-1")
                .build();
        assertThat(support.extractToken(bearer)).isEqualTo("gw-token-1");

        // Bearer 前缀大小写不敏感
        MockServerHttpRequest lower = MockServerHttpRequest.get("/x")
                .header(HttpHeaders.AUTHORIZATION, "bearer gw-token-2")
                .build();
        assertThat(support.extractToken(lower)).isEqualTo("gw-token-2");

        MockServerHttpRequest custom = MockServerHttpRequest.get("/x")
                .header("satoken", "gw-token-3")
                .build();
        assertThat(support.extractToken(custom)).isEqualTo("gw-token-3");

        MockServerHttpRequest none = MockServerHttpRequest.get("/x").build();
        assertThat(support.extractToken(none)).isEmpty();

        MockServerHttpRequest blankBearer = MockServerHttpRequest.get("/x")
                .header(HttpHeaders.AUTHORIZATION, "Bearer   ")
                .build();
        assertThat(support.extractToken(blankBearer)).isEmpty();
    }

    @Test
    @DisplayName("会话解析：有效返回会话，过期与不存在返回 empty")
    void resolveFiltersExpiredSessions() {
        sessions.put("good", new GatewaySession("p-token", 1L, "admin", "ADMIN",
                System.currentTimeMillis() / 1000 + 600));
        sessions.put("expired", new GatewaySession("p-token", 1L, "admin", "ADMIN",
                System.currentTimeMillis() / 1000 - 10));

        StepVerifier.create(support.resolve("good"))
                .assertNext(session -> assertThat(session.platformToken()).isEqualTo("p-token"))
                .verifyComplete();
        StepVerifier.create(support.resolve("expired")).verifyComplete();
        StepVerifier.create(support.resolve("missing")).verifyComplete();
        StepVerifier.create(support.resolve("")).verifyComplete();
    }

    @Test
    @DisplayName("身份登记：不改写请求头、不写响应头，只把身份放进请求属性")
    void authenticatedRegistersPrincipal() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/platform/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer gw-session-token-abcdef")
                        .build());
        GatewaySession session = new GatewaySession("platform-token", 7L, "admin", "ADMIN", 0L);

        var authenticated = support.authenticated(exchange, "platform-token", session);

        // 不改写请求头：网关会话 token 就是平台 token，客户端带来的 Authorization 可原样透传
        assertThat(authenticated.getRequest().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer gw-session-token-abcdef");

        SessionPrincipal principal = support.principal(authenticated);
        assertThat(principal).isNotNull();
        assertThat(principal.platformToken()).isEqualTo("platform-token");
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.sessionToken()).isEqualTo("platform-token");

        // 鉴权阶段不写任何响应头：真机下代理路由的响应头在写出上游响应时已变只读，
        // 在这里 set 会抛 UnsupportedOperationException（现象是「平台侧 200 但客户端连接被关闭」，实测踩过）
        assertThat(authenticated.getResponse().getHeaders())
                .doesNotContainKey("X-Gateway-Session");
    }

    @Test
    @DisplayName("401 信封：状态 401、JSON、含 UNAUTHORIZED 与 traceId")
    void unauthorizedWritesEnvelope() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/agents").build());
        exchange.getAttributes().put(GatewayAttributes.ATTR_TRACE_ID, "trace-401");

        StepVerifier.create(support.unauthorized(exchange, "缺少 token")).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
                .contains("\"code\":\"" + GatewayErrorCode.UNAUTHORIZED.name() + "\"")
                .contains("\"traceId\":\"trace-401\"")
                .contains("\"data\":null");
        // 不外露失败原因细节
        assertThat(body).doesNotContain("缺少 token");
    }

    @Test
    @DisplayName("未鉴权时读取身份返回 null（调用方需自行判空）")
    void principalAbsentBeforeAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/gateway/status").build());
        assertThat(support.principal(exchange)).isNull();
    }
}

