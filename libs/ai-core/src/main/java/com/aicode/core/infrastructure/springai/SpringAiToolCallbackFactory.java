package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * Spring AI 工具回调工厂。两类输出：
 * <ul>
 *   <li>{@link #toDefinitionCallbacks}：只暴露工具定义（名称/描述/JSON Schema），供模型选工具；配合关闭模型内部执行，由编排层自行执行。</li>
 *   <li>{@link #toExecutableCallbacks}：把领域 {@link Tool} 包成可执行回调（解析 JSON 参数 → 执行 → 输出），供 MCP Server 暴露。</li>
 * </ul>
 */
public class SpringAiToolCallbackFactory {

    private final ObjectMapper objectMapper;

    public SpringAiToolCallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 由领域工具定义生成「仅定义」回调，不执行工具。
     */
    public List<ToolCallback> toDefinitionCallbacks(List<ToolDefinition> definitions) {
        if (definitions == null) {
            return List.of();
        }
        return definitions.stream()
                .map(definition -> (ToolCallback) new DefinitionToolCallback(toSpringDefinition(definition)))
                .toList();
    }

    /**
     * 由领域工具生成可执行回调（供 MCP Server 使用）。
     */
    public List<ToolCallback> toExecutableCallbacks(List<Tool> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> (ToolCallback) new ExecutableToolCallback(toSpringDefinition(tool.definition()), tool, objectMapper))
                .toList();
    }

    private org.springframework.ai.tool.definition.ToolDefinition toSpringDefinition(ToolDefinition definition) {
        return DefaultToolDefinition.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(toJson(definition.parameters()))
                .build();
    }

    private String toJson(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            throw new ToolExecutionException("failed to serialize tool schema", ex);
        }
    }

    /**
     * 仅定义回调：模型内部执行被关闭时永远不会被调用，若被调用说明配置错误，直接失败。
     */
    private static final class DefinitionToolCallback implements ToolCallback {

        private final org.springframework.ai.tool.definition.ToolDefinition definition;

        private DefinitionToolCallback(org.springframework.ai.tool.definition.ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new ToolExecutionException("definition-only tool callback must not be executed: " + definition.name());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

    /**
     * 可执行回调：解析模型给出的参数 JSON，调用领域工具并返回文本结果。
     */
    private static final class ExecutableToolCallback implements ToolCallback {

        private final org.springframework.ai.tool.definition.ToolDefinition definition;
        private final Tool tool;
        private final ObjectMapper objectMapper;

        private ExecutableToolCallback(
                org.springframework.ai.tool.definition.ToolDefinition definition,
                Tool tool,
                ObjectMapper objectMapper
        ) {
            this.definition = definition;
            this.tool = tool;
            this.objectMapper = objectMapper;
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return tool.execute(parseArguments(toolInput)).output();
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
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
}
