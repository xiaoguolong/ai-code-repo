package com.aicode.core.infrastructure.embedding;

import com.aicode.core.domain.exception.EmbeddingException;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.infrastructure.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OpenAI 兼容 embeddings 适配器。换供应商只改 base-url / model，不改用例。
 */
@Component
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
public class OpenAiCompatibleEmbeddingAdapter implements EmbeddingModelPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;

    public OpenAiCompatibleEmbeddingAdapter(
            RestClient embeddingRestClient,
            ObjectMapper objectMapper,
            EmbeddingProperties properties
    ) {
        this.restClient = embeddingRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public float[] embed(String text) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingException("EMBEDDING_API_KEY is not configured");
        }
        try {
            String body = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", properties.model(), "input", text))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new EmbeddingException("embedding HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);
            return parse(body);
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingException("embedding call failed: " + rootMessage(ex), ex);
        }
    }

    private float[] parse(String body) {
        try {
            JsonNode embedding = objectMapper.readTree(body).path("data").path(0).path("embedding");
            if (!embedding.isArray()) {
                throw new EmbeddingException("embedding response missing data[0].embedding");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingException("failed to parse embedding response", ex);
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
