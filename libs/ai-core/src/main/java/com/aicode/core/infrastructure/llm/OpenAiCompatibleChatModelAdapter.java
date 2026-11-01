package com.aicode.core.infrastructure.llm;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek / Qwen / OpenAI 共用的 Chat Completions 适配器。换厂商只改配置，不改用例。
 * 支持原生 Function Calling：下发 tools，解析 tool_calls 与 finish_reason。
 */
@Component("delegateChatModelPort")
@ConditionalOnProperty(prefix = "framework", name = "model-provider", havingValue = "openai-compatible", matchIfMissing = true)
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
    public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatModelException("LLM_API_KEY is not configured");
        }
        try {
            String body = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(messages, options, tools))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new ChatModelException("LLM HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);
            ChatResult result = mapper.map(body);
            if ((result.content() == null || result.content().isBlank()) && result.toolCalls().isEmpty()) {
                log.warn("LLM returned empty content, raw response (no secrets): {}", body);
            }
            return result;
        } catch (ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("LLM call failed: " + rootMessage(ex), ex);
        }
    }

    /**
     * 原生 SSE 增量补全（Week 31）：{@code stream=true} + 逐行 {@code data:} 解析。
     *
     * <p>为什么不用 Spring AI 的 {@code ChatModel.stream}：真机上它把整段文本攒到结束才下发
     * （首字=总耗时）；这里直接用 RestClient 拿响应 InputStream，读一行回调一行。</p>
     *
     * <p>只处理正文增量，不下发 tools（需要工具循环的链路仍走同步 {@link #chat}）。
     * 末包 usage 由 {@code stream_options.include_usage} 打开。</p>
     */
    @Override
    public ChatResult chatStream(
            List<ChatMessage> messages,
            ChatOptions options,
            ChatStreamListener listener
    ) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatModelException("LLM_API_KEY is not configured");
        }
        Map<String, Object> payload = buildPayload(messages, options, List.of());
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new ChatModelException(
                                    "LLM HTTP " + response.getStatusCode().value());
                        }
                        StringBuilder content = new StringBuilder();
                        TokenUsage usage = TokenUsage.unknown();
                        String model = options.model();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (listener.isCancelled()) {
                                    throw new StreamCancelledException(
                                            "llm stream cancelled by listener");
                                }
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring("data:".length()).trim();
                                if (data.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                OpenAiChatResponseMapper.StreamDelta delta = mapper.streamDelta(data);
                                if (!delta.content().isEmpty()) {
                                    content.append(delta.content());
                                    listener.onDelta(delta.content());
                                }
                                if (delta.usage() != null) {
                                    usage = delta.usage();
                                }
                                if (delta.model() != null && !delta.model().isBlank()) {
                                    model = delta.model();
                                }
                            }
                        }
                        if (listener.isCancelled()) {
                            throw new StreamCancelledException("llm stream cancelled by listener");
                        }
                        return new ChatResult(
                                content.toString(), List.of(), FinishReason.STOP, usage, model);
                    });
        } catch (StreamCancelledException | ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("LLM stream failed: " + rootMessage(ex), ex);
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private Map<String, Object> buildPayload(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", options.model());
        payload.put("temperature", options.temperature());
        payload.put("max_tokens", options.maxTokens());
        payload.put("messages", messages.stream().map(this::toPayloadMessage).toList());
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools.stream().map(this::toPayloadTool).toList());
        }
        return payload;
    }

    private Map<String, Object> toPayloadMessage(ChatMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", message.role().apiValue());
        if (message.role() == MessageRole.TOOL) {
            map.put("tool_call_id", message.toolCallId());
            map.put("content", message.content());
            return map;
        }
        if (!message.toolCalls().isEmpty()) {
            map.put("content", null);
            map.put("tool_calls", message.toolCalls().stream().map(this::toPayloadToolCall).toList());
            return map;
        }
        map.put("content", message.content());
        return map;
    }

    private Map<String, Object> toPayloadToolCall(ToolCall call) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", call.name());
        function.put("arguments", call.arguments() == null ? "" : call.arguments());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", call.id());
        map.put("type", "function");
        map.put("function", function);
        return map;
    }

    private Map<String, Object> toPayloadTool(ToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "function");
        map.put("function", function);
        return map;
    }
}
