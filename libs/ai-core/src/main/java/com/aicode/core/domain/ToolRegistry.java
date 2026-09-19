package com.aicode.core.domain;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（{@link ToolPort} 的通用实现）。持有多工具，按 name 分发，是所有工具执行的唯一咽喉点，
 * 后续权限/脱敏/限流在此统一拦截。未知工具或参数非法抛 {@link ToolExecutionException}。
 */
public class ToolRegistry implements ToolPort {

    private final Map<String, Tool> tools;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<Tool> tools, ObjectMapper objectMapper) {
        Map<String, Tool> registry = new LinkedHashMap<>();
        for (Tool tool : tools) {
            registry.put(tool.definition().name(), tool);
        }
        this.tools = Map.copyOf(registry);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    @Override
    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            throw new ToolExecutionException("unknown tool: " + call.name());
        }
        return tool.execute(parseArguments(call.arguments()));
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new ToolExecutionException("invalid tool arguments: " + arguments, ex);
        }
    }
}
