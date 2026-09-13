package com.aicode.patient.infrastructure.llm;

import com.aicode.patient.domain.exception.ChatModelException;
import com.aicode.patient.domain.model.ChatMessage;
import com.aicode.patient.domain.model.ChatOptions;
import com.aicode.patient.domain.model.ChatResult;
import com.aicode.patient.domain.port.ChatModelPort;
import com.aicode.patient.infrastructure.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek / Qwen / OpenAI 共用的 Chat Completions 适配器。换厂商只改配置，不改用例。
 */
@Component
public class OpenAiCompatibleChatModelAdapter implements ChatModelPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModelAdapter.class);

    private final RestClient restClient;
    private final OpenAiChatResponseMapper mapper;
    private final LlmProperties properties;

    public OpenAiCompatibleChatModelAdapter(
            RestClient llmRestClient,
            OpenAiChatResponseMapper mapper,
            LlmProperties properties
    ) {
        this.restClient = llmRestClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * 调用 {baseUrl}/chat/completions。baseUrl 按 OpenAI 兼容约定应含 /v1。
     * 密钥只放请求头，禁止写入日志。
     */
    @Override
    public ChatResult chat(List<ChatMessage> messages, ChatOptions options) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatModelException("LLM_API_KEY is not configured");
        }
        try {
            String body = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(messages, options))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new ChatModelException("LLM HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);
            com.aicode.patient.domain.model.ChatResult result = mapper.map(body);
            if (result.content() == null || result.content().isBlank()) {
                log.warn("LLM returned empty content, raw response (no secrets): {}", body);
            }
            return result;
        } catch (ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("LLM call failed: " + rootMessage(ex), ex);
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private Map<String, Object> buildPayload(List<ChatMessage> messages, ChatOptions options) {
        List<Map<String, String>> payloadMessages = messages.stream()
                .map(message -> Map.of(
                        "role", message.role().apiValue(),
                        "content", message.content()
                ))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", options.model());
        payload.put("temperature", options.temperature());
        payload.put("max_tokens", options.maxTokens());
        payload.put("messages", payloadMessages);
        return payload;
    }
}
