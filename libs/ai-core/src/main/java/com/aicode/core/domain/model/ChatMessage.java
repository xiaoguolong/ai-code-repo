package com.aicode.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 单条对话消息。role 与 content 成对出现，供编排层组装上下文。
 * 为原生 Function Calling 扩展：assistant 消息可携带 tool_calls（content 允许为 null），
 * tool 消息通过 toolCallId 关联到具体工具调用。
 */
public record ChatMessage(MessageRole role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        if (toolCalls == null) {
            toolCalls = List.of();
        }
        toolCalls = List.copyOf(toolCalls);
    }

    /**
     * 普通文本消息（system/user/assistant 文本）。
     */
    public ChatMessage(MessageRole role, String content) {
        this(role, content, null, List.of());
    }

    /**
     * 工具执行结果回填消息。
     *
     * @param toolCallId 关联的模型 tool_call id
     * @param content    工具返回内容
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(MessageRole.TOOL, content, toolCallId, List.of());
    }

    /**
     * 携带工具调用的 assistant 消息（content 为 null）。
     */
    public static ChatMessage assistant(List<ToolCall> calls) {
        return new ChatMessage(MessageRole.ASSISTANT, null, null, List.copyOf(calls));
    }
}
