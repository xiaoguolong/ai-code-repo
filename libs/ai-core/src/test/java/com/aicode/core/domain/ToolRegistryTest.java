package com.aicode.core.domain;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具注册表单元测试。
 */
class ToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final class EchoTool implements Tool {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("EchoTool", "回声", Map.of("type", "object"));
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments) {
            return new ToolResult("echo:" + arguments.get("text"));
        }
    }

    @Test
    void listsDefinitionsAndDispatchesByName() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()), objectMapper);

        assertThat(registry.definitions()).hasSize(1);
        assertThat(registry.definitions().get(0).name()).isEqualTo("EchoTool");

        ToolResult result = registry.execute(new ToolCall("call-1", "EchoTool", "{\"text\":\"hi\"}"));
        assertThat(result.output()).isEqualTo("echo:hi");
    }

    @Test
    void throwsForUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()), objectMapper);

        assertThatThrownBy(() -> registry.execute(new ToolCall("call-1", "MissingTool", "{}")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void throwsForInvalidArguments() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()), objectMapper);

        assertThatThrownBy(() -> registry.execute(new ToolCall("call-1", "EchoTool", "not-json")))
                .isInstanceOf(ToolExecutionException.class);
    }
}
