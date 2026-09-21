package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 消息/响应映射器（无状态）。领域 {@link ChatMessage} 与 Spring AI {@code Message} 互转，
 * 并把 Spring AI {@link ChatResponse} 映射为领域 {@link ChatResult}。厂商细节不越过本类。
 */
public class SpringAiMessageMapper {

    /**
     * 领域消息列表转 Spring AI 消息列表。null/空返回空列表，永不返回 null。
     */
    public List<Message> toSpringMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (ChatMessage message : messages) {
            result.add(toSpringMessage(message));
        }
        return result;
    }

    private Message toSpringMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content() == null ? "" : message.content());
            case USER -> new UserMessage(message.content() == null ? "" : message.content());
            case ASSISTANT -> toAssistant(message);
            case TOOL -> toToolResponse(message);
        };
    }

    private AssistantMessage toAssistant(ChatMessage message) {
        List<AssistantMessage.ToolCall> toolCalls = message.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
                .toList();
        return new AssistantMessage(message.content() == null ? "" : message.content(), Map.of(), toolCalls);
    }

    private ToolResponseMessage toToolResponse(ChatMessage message) {
        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(
                message.toolCallId() == null ? "" : message.toolCallId(),
                "",
                message.content() == null ? "" : message.content());
        return new ToolResponseMessage(List.of(response));
    }

    /**
     * Spring AI 响应映射为领域结果。
     *
     * @throws ChatModelException 响应为空或缺少候选结果
     */
    public ChatResult toChatResult(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new ChatModelException("spring ai returned empty response");
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation.getOutput();
        String content = output == null ? null : output.getText();
        List<ToolCall> toolCalls = output == null || output.getToolCalls() == null
                ? List.of()
                : output.getToolCalls().stream()
                        .map(call -> new ToolCall(call.id(), call.name(), call.arguments()))
                        .toList();
        FinishReason finishReason = !toolCalls.isEmpty()
                ? FinishReason.TOOL_CALLS
                : FinishReason.fromApi(finishReason(generation));
        TokenUsage usage = toUsage(response.getMetadata() == null ? null : response.getMetadata().getUsage());
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatResult(content, toolCalls, finishReason, usage, model);
    }

    private String finishReason(Generation generation) {
        ChatGenerationMetadata metadata = generation.getMetadata();
        return metadata == null ? null : metadata.getFinishReason();
    }

    private TokenUsage toUsage(Usage usage) {
        if (usage == null || usage.getPromptTokens() == null || usage.getCompletionTokens() == null) {
            return TokenUsage.unknown();
        }
        int prompt = usage.getPromptTokens();
        int completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        return new TokenUsage(prompt, completion, total == null ? prompt + completion : total);
    }
}
