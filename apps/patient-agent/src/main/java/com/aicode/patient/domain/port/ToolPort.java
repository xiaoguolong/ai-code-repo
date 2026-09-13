package com.aicode.patient.domain.port;

import com.aicode.patient.domain.model.ToolCall;
import com.aicode.patient.domain.model.ToolDefinition;
import com.aicode.patient.domain.model.ToolResult;

import java.util.List;

/**
 * 出站端口：工具契约。第 6 周 Tool Calling 才提供实现，本周仅接口预留，禁止空实现。
 */
public interface ToolPort {

    /**
     * 列出可用工具定义，供模型选工具（第 6 周实现）。
     */
    List<ToolDefinition> definitions();

    /**
     * 执行一次工具调用并返回结果（第 6 周实现）。
     */
    ToolResult execute(ToolCall call);
}
