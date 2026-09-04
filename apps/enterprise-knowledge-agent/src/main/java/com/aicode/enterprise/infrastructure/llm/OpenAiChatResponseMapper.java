package com.aicode.enterprise.infrastructure.llm;

import com.aicode.enterprise.domain.model.ChatResult;
import com.aicode.enterprise.domain.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将 OpenAI 兼容的 chat/completions JSON 映射为领域 ChatResult。
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
     * @return 内容与 usage；usage 缺失时记 0
     * @throws IllegalStateException choices 为空或 JSON 非法
     */
    public ChatResult map(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("LLM response has no choices");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            String model = root.path("model").asText("");
            JsonNode usageNode = root.get("usage");
            TokenUsage usage = usageNode == null || usageNode.isNull()
                    ? TokenUsage.unknown()
                    : new TokenUsage(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0),
                    usageNode.path("total_tokens").asInt(0)
            );
            return new ChatResult(content, usage, model);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LLM response parse failed", ex);
        }
    }
}
