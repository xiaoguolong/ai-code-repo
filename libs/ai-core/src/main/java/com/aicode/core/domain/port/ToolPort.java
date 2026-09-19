package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;

import java.util.List;

/**
 * 出站端口：工具契约。定义可用工具供模型选择，并执行一次工具调用返回结果。
 * 实现类负责按 name 分发到具体工具；未知工具必须抛 {@link ToolExecutionException}。
 */
public interface ToolPort {

    /**
     * 列出可用工具定义（含 JSON Schema 参数），供模型选工具。
     */
    List<ToolDefinition> definitions();

    /**
     * 执行一次工具调用并返回结果。
     *
     * @throws ToolExecutionException 未知工具或参数非法
     */
    ToolResult execute(ToolCall call);
}
