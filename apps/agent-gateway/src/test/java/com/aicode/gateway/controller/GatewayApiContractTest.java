package com.aicode.gateway.controller;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.support.FakeCacheStore;
import com.aicode.gateway.support.FakePlatformClient;
import com.aicode.gateway.support.FakeSessionStore;
import com.aicode.gateway.support.GatewayTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关 HTTP 契约测试（Week 19）。
 *
 * <p>用真实 WebFlux 管线（过滤器 + Controller + 异常处理）覆盖：统一信封形状、登录建会话、
 * 鉴权 401、上游 4xx 透传 / 5xx 收敛、链路 ID 回显。</p>
 *
 * <p><b>为什么用 {@link TestRestTemplate} 而不是 WebTestClient</b>：在
 * {@code webEnvironment = RANDOM_PORT} 下，{@code WebTestClient} 的断言链会对同一请求发起
 * 第二次订阅，而第二次订阅时响应头已变成只读（{@code ReadOnlyHttpHeaders}），
 * 于是「鉴权成功后写响应头」的代码会在测试里抛 {@code UnsupportedOperationException}
 * （真实容器不会）。阻塞式客户端只发一次请求，既贴近真实调用方，也避免这类测试框架噪声
 * （实测踩坑，见实现日志）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GatewayTestConfiguration.class)
class GatewayApiContractTest {

    private static final String LOGIN_BODY = "{\"username\":\"admin\",\"password\":\"secret\"}";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FakePlatformClient platformClient;

    @Autowired
    private FakeSessionStore sessionStore;

    @Autowired
    private FakeCacheStore cacheStore;

    @BeforeEach
    void reset() {
        platformClient.loginFailure = null;
        platformClient.listFailure = null;
        platformClient.runFailure = null;
        platformClient.listCount = 0;
        platformClient.runCount = 0;
        cacheStore.failing = false;
        // 假实现是 Spring 单例：不清理会让会话数/回源次数随执行顺序漂移
        sessionStore.clear();
        cacheStore.clear();
    }

    @Test
    @DisplayName("登录成功：200 + SUCCESS 信封 + 会话 token，响应体不含平台 token")
    void loginReturnsSessionToken() {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Trace-Id", "week19-login");

        ResponseEntity<String> response = post("/api/v1/gateway/sessions", headers, LOGIN_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo("week19-login");
        assertThat(response.getBody())
                .contains("\"code\":\"SUCCESS\"")
                .contains("\"traceId\":\"week19-login\"")
                .contains("\"tokenName\":\"Authorization\"")
                .contains("\"username\":\"admin\"")
                .doesNotContain("\"platformToken\":");
    }

    @Test
    @DisplayName("入参校验失败：400 VALIDATION_ERROR，data 为 null")
    void loginValidatesInput() {
        ResponseEntity<String> response = post("/api/v1/gateway/sessions", jsonHeaders(),
                "{\"username\":\"\",\"password\":\"\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"data\":null");
    }

    @Test
    @DisplayName("平台 401：透传 UNAUTHORIZED 与 401（不收敛为 502）")
    void loginPropagatesUnauthorized() {
        platformClient.loginFailure = new PlatformBusinessException("UNAUTHORIZED", 401, "账号或密码错误");

        ResponseEntity<String> response = post("/api/v1/gateway/sessions", jsonHeaders(), LOGIN_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("\"data\":null");
    }

    @Test
    @DisplayName("平台不可达：502 GATEWAY_UPSTREAM_ERROR")
    void loginMapsUnreachableToBadGateway() {
        platformClient.loginFailure = FakePlatformClient.unreachable();

        ResponseEntity<String> response = post("/api/v1/gateway/sessions", jsonHeaders(), LOGIN_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("\"code\":\"GATEWAY_UPSTREAM_ERROR\"");
    }

    @Test
    @DisplayName("平台 5xx：收敛为 UPSTREAM_SERVER_ERROR + 502")
    void loginMapsServerErrorToBadGateway() {
        platformClient.loginFailure = new GatewayUpstreamException(
                GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, "平台返回 500", "INTERNAL_ERROR", 500);

        ResponseEntity<String> response = post("/api/v1/gateway/sessions", jsonHeaders(), LOGIN_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("\"code\":\"UPSTREAM_SERVER_ERROR\"");
    }

    @Test
    @DisplayName("未带会话访问业务端点：401 UNAUTHORIZED（含 traceId）")
    void businessEndpointRequiresSession() {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Trace-Id", "week19-noauth");

        ResponseEntity<String> response = get("/api/v1/gateway/agents", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("\"traceId\":\"week19-noauth\"");
    }

    @Test
    @DisplayName("登录后带会话读目录：200、首访回源、二访命中缓存")
    void agentCatalogUsesCacheAfterLogin() {
        String token = login();

        ResponseEntity<String> first = get("/api/v1/gateway/agents", bearer(token));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody())
                .contains("\"code\":\"SUCCESS\"")
                .contains("\"agentKey\":\"medical-assistant\"");

        ResponseEntity<String> second = get("/api/v1/gateway/agents", bearer(token));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(platformClient.listCount).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh=true 强制回源")
    void agentCatalogRefreshBypassesCache() {
        String token = login();

        assertThat(get("/api/v1/gateway/agents", bearer(token)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/gateway/agents?refresh=true", bearer(token)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(platformClient.listCount).isEqualTo(2);
    }

    @Test
    @DisplayName("登录后触发执行：200 + 平台执行记录透传，traceId 透传给上游")
    void runAgentThroughGateway() {
        String token = login();
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", "week19-run");

        ResponseEntity<String> response = post("/api/v1/gateway/agents/medical-assistant/runs",
                headers, "{\"input\":{\"patientId\":\"P001\"}}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"code\":\"SUCCESS\"")
                .contains("\"traceId\":\"week19-run\"")
                .contains("\"executionId\":\"exec-1\"");
        assertThat(platformClient.runCount).isEqualTo(1);
        assertThat(platformClient.lastTraceId).isEqualTo("week19-run");
        assertThat(platformClient.lastInput).containsEntry("patientId", "P001");
    }

    @Test
    @DisplayName("自检端点：返回路由、会话计数、链路上下文；不含平台 token")
    void statusEndpointExposesSelfCheck() {
        String token = login();
        HttpHeaders headers = bearer(token);
        headers.set("X-Trace-Id", "week19-status");

        ResponseEntity<String> response = get("/api/v1/gateway/status", headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"application\":\"agent-gateway\"")
                .contains("\"platformUri\":\"http://platform-under-test:8084\"")
                .contains("\"activeSessions\":1")
                .contains("\"traceId\":\"week19-status\"")
                .contains("\"skywalkingAgent\":false")
                .doesNotContain("\"platformToken\":");
    }

    @Test
    @DisplayName("登出：删除会话，之后访问业务端点 401")
    void logoutInvalidatesSession() {
        String token = login();

        ResponseEntity<String> logout = restTemplate.exchange("/api/v1/gateway/sessions/current",
                HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logout.getBody()).contains("\"code\":\"SUCCESS\"");
        assertThat(sessionStore.size()).isZero();
        assertThat(get("/api/v1/gateway/agents", bearer(token)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("会话头也支持 `Authorization: <token>` 裸写法（兼容不发 Bearer 前缀的客户端）")
    void bareAuthorizationHeaderIsAccepted() {
        String token = login();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, token);

        assertThat(get("/api/v1/gateway/agents", headers).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** 登录并返回网关联会话 token。 */
    private String login() {
        ResponseEntity<String> response = post("/api/v1/gateway/sessions", jsonHeaders(), LOGIN_BODY);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extract(response.getBody(), "sessionToken");
    }

    /** JSON 请求头。 */
    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** 带 Bearer 会话头的请求头。 */
    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    /** 发起 POST。 */
    private ResponseEntity<String> post(String path, HttpHeaders headers, String body) {
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    /** 发起 GET。 */
    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** 从 JSON 文本里取字符串字段（测试里不引 JSON 解析器，避免断言依赖序列化细节）。 */
    private static String extract(String json, String field) {
        String body = json == null ? "" : json;
        String needle = "\"" + field + "\":\"";
        int index = body.indexOf(needle);
        if (index < 0) {
            throw new IllegalStateException("响应里没有字段 " + field + "：" + body);
        }
        int start = index + needle.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
