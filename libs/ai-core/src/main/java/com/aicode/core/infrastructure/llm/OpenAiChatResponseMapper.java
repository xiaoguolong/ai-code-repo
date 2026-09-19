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
}
