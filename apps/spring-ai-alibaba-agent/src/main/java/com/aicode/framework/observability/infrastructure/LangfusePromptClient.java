package com.aicode.framework.observability.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Langfuse Prompt 管理 API 客户端（Week 18）。
 *
 * <p>只做一件事：{@code GET /api/public/v2/prompts/{name}?label=<label>}。
 * 设计要点：</p>
 * <ul>
 *   <li><b>永不抛异常</b>：404 → miss，其余异常 → fallback，由适配器回退本地模板
 *       （Prompt 服务不能成为业务单点）；</li>
 *   <li>Basic 认证：public key 作用户名、secret key 作密码；</li>
 *   <li>兼容 text 与 chat 两类 Prompt：text 取原文，chat 取 system 消息（我们用它当系统提示），
 *       没有 system 时取第一条有内容的消息；</li>
 *   <li>响应正文不入日志（可能含业务提示词），只记 name / version / 状态。</li>
 * </ul>
 */
public class LangfusePromptClient {

    private static final Logger log = LoggerFactory.getLogger(LangfusePromptClient.class);

    /** 拉取结果：命中。 */
    public static final String OUTCOME_HIT = "hit";

    /** 拉取结果：未找到。 */
    public static final String OUTCOME_MISS = "miss";

    /** 拉取结果：异常回退。 */
    public static final String OUTCOME_FALLBACK = "fallback";

    private static final String PROMPT_PATH = "/api/public/v2/prompts/{name}";

    private final LangfuseProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /**
     * @param properties   Langfuse 配置（host / 密钥 / 标签 / 超时）
     * @param objectMapper JSON 解析器
     */
    public LangfusePromptClient(LangfuseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // 显式走 HTTP/1.1：Langfuse 的 Prompt 端点是明文 HTTP，JDK HttpClient 默认会先尝试 h2c 升级；
        // 在真实局域网路径（VPN / 容器端口转发）下会出现 "HTTP/1.1 header parser received no bytes"，
        // 表现为 Prompt 一直回退本地模板（本地回环测不出来，实测踩过）。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.resolvedTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.resolvedTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.resolvedHost())
                .requestFactory(factory)
                .build();
    }

    /**
     * 按配置的标签拉取 Prompt。
     *
     * @param name Prompt 名，空则直接回退（不发请求）
     * @return 拉取结果，永不为 null
     */
    public LangfusePromptFetch fetch(String name) {
        if (name == null || name.isBlank()) {
            return LangfusePromptFetch.fallback();
        }
        String label = properties.resolvedPrompt().resolvedLabel();
        try {
            String authorization = properties.authorizationHeader();
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(builder -> builder.path(PROMPT_PATH).queryParam("label", label).build(name));
            if (!authorization.isEmpty()) {
                request = request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            String body = request.retrieve().body(String.class);
            return parse(name, body);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.info("[langfuse] prompt not found in Langfuse, fallback to classpath name={}", name);
                return LangfusePromptFetch.miss();
            }
            log.warn("[langfuse] prompt fetch failed name={} status={}", name, ex.getStatusCode().value());
            return LangfusePromptFetch.fallback();
        } catch (RuntimeException ex) {
            log.warn("[langfuse] prompt fetch failed name={} error={}", name, ex.getMessage());
            return LangfusePromptFetch.fallback();
        }
    }

    /** 解析响应：版本号 + 文本。任一缺失即视为不可用（无法关联版本的 Prompt 没有管理价值）。 */
    private LangfusePromptFetch parse(String name, String body) {
        if (body == null || body.isBlank()) {
            log.warn("[langfuse] prompt response empty name={}", name);
            return LangfusePromptFetch.fallback();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String version = root.path("version").asText("");
            String text = extractText(root.path("prompt"));
            if (version.isBlank() || text == null || text.isBlank()) {
                log.warn("[langfuse] prompt response unusable name={} version={}", name, version);
                return LangfusePromptFetch.fallback();
            }
            return LangfusePromptFetch.hit(new LangfusePromptPayload(text.trim(), version.trim()));
        } catch (Exception ex) {
            log.warn("[langfuse] prompt response parse failed name={} error={}", name, ex.getMessage());
            return LangfusePromptFetch.fallback();
        }
    }

    /** text 类型取字符串；chat 类型取 system 消息，缺失时取第一条有内容的消息。 */
    private String extractText(JsonNode promptNode) {
        if (promptNode == null || promptNode.isMissingNode() || promptNode.isNull()) {
            return null;
        }
        if (promptNode.isTextual()) {
            return promptNode.asText();
        }
        if (promptNode.isArray()) {
            String firstContent = null;
            for (JsonNode item : promptNode) {
                String content = item.path("content").asText(null);
                if (content == null || content.isBlank()) {
                    continue;
                }
                if (firstContent == null) {
                    firstContent = content;
                }
                if ("system".equalsIgnoreCase(item.path("role").asText(""))) {
                    return content;
                }
            }
            return firstContent;
        }
        return null;
    }
}
