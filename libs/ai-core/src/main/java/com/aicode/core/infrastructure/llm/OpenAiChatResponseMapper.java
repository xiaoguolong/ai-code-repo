package com.aicode.core.infrastructure.llm;

import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 OpenAI 兼容的 chat/completions JSON 映射为领域 ChatResult（含 tool_calls 与 finish_reason）。
 */
public class OpenAiChatResponseMapper {

    private final ObjectMapper objectMapper;

    public OpenAiChatResponseMapper() {
        this(new ObjectMapper());
    }

    public OpenAiChatResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param json 厂商响应体
     * @return 内容、工具调用、结束原因与 usage；usage 缺失时记 0
     * @throws IllegalStateException choices 为空或 JSON 非法
     */
    public ChatResult map(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("LLM response has no choices");
            }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            if (content.isBlank()) {
                content = message.path("reasoning_content").asText("");
            }
            List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
            String finishReason = choice.path("finish_reason").asText("");
            String model = root.path("model").asText("");
            JsonNode usageNode = root.get("usage");
            TokenUsage usage = usageNode == null || usageNode.isNull()
                    ? TokenUsage.unknown()
                    : new TokenUsage(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0),
                    usageNode.path("total_tokens").asInt(0)
            );
            return new ChatResult(content, toolCalls, FinishReason.fromApi(finishReason), usage, model);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LLM response parse failed", ex);
        }
    }

    private List<ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode node : toolCallsNode) {
            JsonNode function = node.path("function");
            toolCalls.add(new ToolCall(
                    node.path("id").asText(""),
                    function.path("name").asText(""),
                    function.path("arguments").asText("")
            ));
        }
        return List.copyOf(toolCalls);
    }

    /**
     * 解析一条流式 chunk（SSE {@code data:} 后的 JSON）。
     *
     * <p>OpenAI 兼容协议的末包可能只有 {@code usage}、{@code choices} 为空数组；
     * 首个包可能只有 role、content 为空。这两种都要能安全解析。</p>
     *
     * @param json 单条 chunk JSON
     * @return 增量文本、用量（可能为 null）、模型名
     * @throws IllegalStateException JSON 非法
     */
    public StreamDelta streamDelta(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            StringBuilder content = new StringBuilder();
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                String text = delta.path("content").asText("");
                if (text.isBlank()) {
                    text = delta.path("reasoning_content").asText("");
                }
                content.append(text);
            }
            TokenUsage usage = null;
            JsonNode usageNode = root.get("usage");
            if (usageNode != null && usageNode.isObject()) {
                usage = new TokenUsage(
                        usageNode.path("prompt_tokens").asInt(0),
                        usageNode.path("completion_tokens").asInt(0),
                        usageNode.path("total_tokens").asInt(0));
            }
            return new StreamDelta(content.toString(), usage, root.path("model").asText(""));
        } catch (Exception ex) {
            throw new IllegalStateException("LLM stream chunk parse failed", ex);
        }
    }

    /**
     * 单条流式 chunk 的解析结果。
     *
     * @param content 增量正文（可能为空串）
     * @param usage   用量；只有带 usage 的包才有值，否则 null
     * @param model   模型名（可能为空）
     */
    public record StreamDelta(String content, TokenUsage usage, String model) {
    }
}
