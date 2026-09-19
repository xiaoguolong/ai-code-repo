package com.aicode.core.domain.model;

import java.util.Objects;

/**
 * 工具调用请求。id 由模型返回，用于把工具结果回填到对应消息（tool_call_id 关联）。
 *
 * @param name      工具名，对应 {@link ToolDefinition#name()}
 * @param arguments 工具参数 JSON 字符串（模型返回原文，由执行侧解析）
 */
public record ToolCall(String id, String name, String arguments) {

    public ToolCall {
        Objects.requireNonNull(name, "name");
    }
}
