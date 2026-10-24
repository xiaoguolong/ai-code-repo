package com.aicode.enterprise.infrastructure.sso;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.enterprise.domain.exception.SsoUnavailableException;
import com.aicode.enterprise.domain.model.PlatformIdentity;
import com.aicode.enterprise.domain.port.PlatformIdentityPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台身份客户端（Week 21）：调用 {@code POST /api/v1/platform/auth/verify}。
 *
 * <p>状态码语义（写死并单测）：</p>
 * <ul>
 *   <li>200 → 解析信封，{@code data} 缺失视为协议异常（SsoUnavailable）；</li>
 *   <li>401 → {@link AuthenticationException}（凭据错误）；</li>
 *   <li>其它（含 5xx）→ {@link SsoUnavailableException}；</li>
 *   <li>网络/超时 → {@link SsoUnavailableException}。</li>
 * </ul>
 *
 * <p>不记录请求体与密码；日志只留「平台返回了什么状态码」。</p>
 */
@Component
public class HttpPlatformIdentityAdapter implements PlatformIdentityPort {

    private static final Logger log = LoggerFactory.getLogger(HttpPlatformIdentityAdapter.class);

    static final String VERIFY_PATH = "/api/v1/platform/auth/verify";

    private final SsoProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpPlatformIdentityAdapter(SsoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build();
    }

    @Override
    public PlatformIdentity authenticate(String username, String password) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.platformBaseUrl() + VERIFY_PATH))
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(username, password)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) {
                throw new AuthenticationException("invalid credentials");
            }
            if (status != 200) {
                log.warn("[sso] platform verify returned status={}", status);
                throw new SsoUnavailableException("platform verify failed: status " + status);
            }
            return parseIdentity(response.body());
        } catch (IOException ex) {
            log.warn("[sso] platform verify unreachable error={}", ex.getMessage());
            throw new SsoUnavailableException("platform verify unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SsoUnavailableException("platform verify interrupted", ex);
        }
    }

    private String requestBody(String username, String password) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("username", username == null ? "" : username);
        payload.put("password", password == null ? "" : password);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new SsoUnavailableException("failed to serialize sso request", ex);
        }
    }

    private PlatformIdentity parseIdentity(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!"SUCCESS".equals(root.path("code").asText()) || data.isMissingNode() || data.isNull()) {
                throw new SsoUnavailableException("unexpected verify response envelope");
            }
            return new PlatformIdentity(
                    data.path("userId").asLong(),
                    data.path("username").asText(),
                    data.path("roleKey").asText(),
                    data.path("tenantKey").asText());
        } catch (IOException ex) {
            throw new SsoUnavailableException("failed to parse verify response", ex);
        } catch (IllegalArgumentException ex) {
            throw new SsoUnavailableException("invalid identity in verify response", ex);
        }
    }
}
