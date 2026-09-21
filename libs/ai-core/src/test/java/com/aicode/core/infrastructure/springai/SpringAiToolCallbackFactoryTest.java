package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring AI 工具回调工厂测试：领域定义转出可暴露给模型的 ToolCallback，领域 {@link Tool} 转出可执行回调。
 */
class SpringAiToolCallbackFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpringAiToolCallbackFactory factory = new SpringAiToolCallbackFactory(objectMapper);

    @Test
    void exposesDefinitionWithoutExecution() {
        ToolDefinition definition = new ToolDefinition(
                "WeatherTool", "查询天气", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));

        List<ToolCallback> callbacks = factory.toDefinitionCallbacks(List.of(definition));

        assertThat(callbacks).hasSize(1);
        org.springframework.ai.tool.definition.ToolDefinition springDefinition = callbacks.get(0).getToolDefinition();
        assertThat(springDefinition.name()).isEqualTo("WeatherTool");
        assertThat(springDefinition.description()).isEqualTo("查询天气");
        assertThat(springDefinition.inputSchema()).contains("\"city\"");
    }

    @Test
    void executableCallbackParsesArgumentsAndInvokesTool() {
        Tool echo = new Tool() {
            @Override
            public ToolDefinition definition() {
                return new ToolDefinition("EchoTool", "回显", Map.of("type", "object"));
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                return new ToolResult("echo:" + arguments.get("q"));
            }
        };

        List<ToolCallback> callbacks = factory.toExecutableCallbacks(List.of(echo));

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0).getToolDefinition().name()).isEqualTo("EchoTool");
        assertThat(callbacks.get(0).call("{\"q\":\"hi\"}")).isEqualTo("echo:hi");
        // MCP Server 走两参重载 call(input, ToolContext)，必须同样可执行
        assertThat(callbacks.get(0).call("{\"q\":\"mcp\"}", new ToolContext(Map.of()))).isEqualTo("echo:mcp");
    }
}
