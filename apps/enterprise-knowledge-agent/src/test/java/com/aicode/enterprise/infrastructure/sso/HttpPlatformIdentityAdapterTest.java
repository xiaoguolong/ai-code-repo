package com.aicode.enterprise.infrastructure.sso;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.enterprise.domain.exception.SsoUnavailableException;
import com.aicode.enterprise.domain.model.PlatformIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台身份客户端：状态码语义、信封解析、协议异常统一收敛为「身份源不可用」。
 */
class HttpPlatformIdentityAdapterTest {

    private HttpServer server;
    private HttpPlatformIdentityAdapter adapter;
    private String lastBody = "";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/platform/auth/verify", this::handle);
        server.start();
        adapter = new HttpPlatformIdentityAdapter(
                new SsoProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), 2000, true),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesIdentityFromPlatformEnvelope() {
        serveStatus = 200;
        serveBody = """
                {"code":"SUCCESS","message":"OK","data":{"userId":2,"username":"operator",
                "roleKey":"operator","tenantKey":"default"},"traceId":"t-1"}
                """;

        PlatformIdentity identity = adapter.authenticate("operator", "operator123");

        assertThat(identity.userId()).isEqualTo(2L);
        assertThat(identity.username()).isEqualTo("operator");
        assertThat(identity.roleKey()).isEqualTo("operator");
        assertThat(identity.tenantKey()).isEqualTo("default");
        assertThat(lastBody).contains("\"username\":\"operator\"");
    }

    @Test
    void maps401ToAuthenticationException() {
        serveStatus = 401;
        serveBody = """
                {"code":"UNAUTHORIZED","message":"用户名或密码错误","data":null,"traceId":"t-2"}
                """;

        assertThatThrownBy(() -> adapter.authenticate("operator", "bad"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void maps5xxToSsoUnavailable() {
        serveStatus = 500;
        serveBody = "{\"code\":\"INTERNAL_ERROR\"}";

        assertThatThrownBy(() -> adapter.authenticate("operator", "operator123"))
                .isInstanceOf(SsoUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    void mapsSuccessfulEnvelopeWithoutDataToSsoUnavailable() {
        serveStatus = 200;
        serveBody = "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":null}";

        assertThatThrownBy(() -> adapter.authenticate("operator", "operator123"))
                .isInstanceOf(SsoUnavailableException.class);
    }

    @Test
    void mapsUnreachablePlatformToSsoUnavailable() {
        server.stop(0);
        HttpPlatformIdentityAdapter broken = new HttpPlatformIdentityAdapter(
                new SsoProperties(true, "http://127.0.0.1:1", 500, true), new ObjectMapper());

        assertThatThrownBy(() -> broken.authenticate("operator", "operator123"))
                .isInstanceOf(SsoUnavailableException.class);
    }

    private volatile int serveStatus = 200;
    private volatile String serveBody = "{}";

    private void handle(HttpExchange exchange) throws IOException {
        lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = serveBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(serveStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
