package com.aicode.core.domain.model;

import java.util.List;

/**
 * 模型适配器的统一出参。屏蔽厂商 JSON，领域只认本类型。
 * 为原生 Function Calling 扩展：content 可能为空（工具调用轮），toolCalls 携带模型要执行的工具调用，
 * finishReason 区分本轮是结束还是要求调用工具。
 */
public record ChatResult(
        String content,
        List<ToolCall> toolCalls,
        FinishReason finishReason,
        TokenUsage usage,
        String model
) {

    public ChatResult {
        if (toolCalls == null) {
            toolCalls = List.of();
        }
        toolCalls = List.copyOf(toolCalls);
        if (finishReason == null) {
            finishReason = FinishReason.UNKNOWN;
        }
    }

    /**
     * 本轮是否要求调用工具（而非结束）。
     */
    public boolean hasToolCalls() {
        return finishReason == FinishReason.TOOL_CALLS && !toolCalls.isEmpty();
    }
}
