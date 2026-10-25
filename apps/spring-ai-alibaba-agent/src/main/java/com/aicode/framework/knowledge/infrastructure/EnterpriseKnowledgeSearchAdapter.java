package com.aicode.framework.knowledge.infrastructure;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchHit;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchQuery;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * enterprise 内部知识库适配器（Week 25）。
 *
 * <p>调用 {@code /api/v1/internal/knowledge/**}（X-Internal-Token 守卫、只读）。
 * 平台侧**不落知识库正文**，也不直接调用 enterprise 的 RAG 问答（那条路会绕过平台的
 * Guardrail / 审计 / 执行记录），只拿切片自己生成答案。</p>
 *
 * <p>失败语义：配置缺失、上游 4xx/5xx、超时或报文异常统一抛
 * {@link ToolExecutionException}（对外 502），不返回空结果冒充「没有相关内容」。</p>
 */
public class EnterpriseKnowledgeSearchAdapter implements KnowledgeSearchPort {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseKnowledgeSearchAdapter.class);

    /** 内部令牌请求头（必须与 enterprise 的 InternalTokenGuard 一致）。 */
    static final String HEADER_NAME = "X-Internal-Token";

    private final RestClient restClient;
    private final PlatformKnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public EnterpriseKnowledgeSearchAdapter(
            PlatformKnowledgeProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(properties.getTimeoutMs(), 500);
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public KnowledgeBaseCatalog listBases(long platformUserId) {
        ensureConfigured();
        try {
            String body = restClient.get()
                    .uri(builder -> builder.path("/api/v1/internal/knowledge/bases")
                            .queryParam("platformUserId", platformUserId)
                            .build())
                    .header(HEADER_NAME, properties.getInternalToken())
                    .retrieve()
                    .body(String.class);
            return catalogOf(body);
        } catch (RestClientResponseException ex) {
            throw new ToolExecutionException(
                    "knowledge service rejected list-bases: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ToolExecutionException("knowledge service unavailable: list-bases", ex);
        }
    }

    @Override
    public KnowledgeBaseCatalog listBasesByEnterpriseUser(long enterpriseUserId) {
        ensureConfigured();
        try {
            String body = restClient.get()
                    .uri(builder -> builder.path("/api/v1/internal/knowledge/bases")
                            .queryParam("enterpriseUserId", enterpriseUserId)
                            .build())
                    .header(HEADER_NAME, properties.getInternalToken())
                    .retrieve()
                    .body(String.class);
            return catalogOf(body);
        } catch (RestClientResponseException ex) {
            throw new ToolExecutionException(
                    "knowledge service rejected list-bases by enterprise user: "
                            + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ToolExecutionException(
                    "knowledge service unavailable: list-bases by enterprise user", ex);
        }
    }

    private KnowledgeBaseCatalog catalogOf(String body) {
        JsonNode data = dataOf(body);
        List<KnowledgeBaseCatalogEntry> entries = new ArrayList<>();
        for (JsonNode item : data.path("bases")) {
            entries.add(new KnowledgeBaseCatalogEntry(
                    item.path("knowledgeBaseId").asLong(),
                    item.path("name").asText(""),
                    item.path("description").asText(""),
                    item.path("documentCount").asInt(0)));
        }
        return new KnowledgeBaseCatalog(data.path("enterpriseUserId").asLong(), entries);
    }

    @Override
    public List<KnowledgeSearchHit> search(KnowledgeSearchQuery query) {
        ensureConfigured();
        Map<String, Object> payload = Map.of(
                "enterpriseUserId", query.enterpriseUserId(),
                "knowledgeBaseId", query.knowledgeBaseId(),
                "query", query.query(),
                "topK", query.topK());
        try {
            String body = restClient.post()
                    .uri("/api/v1/internal/knowledge/search")
                    .header(HEADER_NAME, properties.getInternalToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode data = dataOf(body);
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (JsonNode item : data.path("hits")) {
                hits.add(new KnowledgeSearchHit(
                        data.path("knowledgeBase").path("knowledgeBaseId").asLong(),
                        item.path("documentId").asText(""),
                        item.path("documentName").asText(""),
                        item.path("chunkIndex").asInt(0),
                        item.path("content").asText(""),
                        item.path("score").asDouble(0.0)));
            }
            log.info("[knowledge] search knowledgeBaseId={} topK={} hits={}",
                    query.knowledgeBaseId(), query.topK(), hits.size());
            return hits;
        } catch (RestClientResponseException ex) {
            throw new ToolExecutionException(
                    "knowledge service rejected search: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ToolExecutionException("knowledge service unavailable: search", ex);
        }
    }

    private JsonNode dataOf(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new ToolExecutionException("knowledge service returned no data");
            }
            return data;
        } catch (ToolExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ToolExecutionException("knowledge service returned invalid json", ex);
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new ToolExecutionException("knowledge capability is disabled");
        }
        if (!properties.configured()) {
            throw new ToolExecutionException(
                    "knowledge internal token is not configured (PLATFORM_KNOWLEDGE_INTERNAL_TOKEN)");
        }
    }
}
