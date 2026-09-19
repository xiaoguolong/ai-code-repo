package com.aicode.core.domain;

import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;

import java.util.Map;

/**
 * 单个工具契约。每个工具声明自己的定义（名称/描述/JSON Schema 参数）并实现执行逻辑，
 * 参数已由注册表解析为 Map。
 */
public interface Tool {

    /**
     * 工具定义，供模型选工具。
     */
    ToolDefinition definition();

    /**
     * 执行工具并返回结果。
     *
     * @param arguments 已解析的参数（key 为参数名，value 为参数值）
     * @return 工具结果，内容为可序列化文本（JSON）
     */
    ToolResult execute(Map<String, Object> arguments);
}
