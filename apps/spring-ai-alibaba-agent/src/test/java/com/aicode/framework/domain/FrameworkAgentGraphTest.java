package com.aicode.framework.domain;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.application.FrameworkRuntimeConfig;
import com.aicode.framework.domain.exception.AgentExecutionException;
import com.aicode.framework.domain.exception.AgentLoopExceededException;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Graph Agent 领域服务测试：不依赖真实模型与网络，用脚本化端口驱动 agent ↔ tools 循环。
 */
class FrameworkAgentGraphTest {

    private static final ChatResult TOOL_CALL = new ChatResult(
            null,
            List.of(new ToolCall("call_1", "PatientLookupTool", "{\"patientId\":\"P001\"}")),
            FinishReason.TOOL_CALLS,
            new TokenUsage(5, 5, 10),
            "test-model");

    private final PromptTemplatePort promptTemplatePort = new PromptTemplatePort() {
        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "系统提示");
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return load(name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of(new PromptDescriptor("agent", "v1"));
        }
    };

    private final ToolPort toolPort = new ToolPort() {
        @Override
        public List<ToolDefinition> definitions() {
            return List.of(new ToolDefinition("PatientLookupTool", "查询患者", Map.of()));
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return new ToolResult("{\"name\":\"张三\"}");
        }
    };

    @Test
    void executesToolThenReturnsAnswer() {
        ChatResult answer = new ChatResult("患者张三，62 岁", List.of(), FinishReason.STOP,
                new TokenUsage(3, 4, 7), "test-model");
        FrameworkAgentGraph graph = new FrameworkAgentGraph(
                new ScriptedChatModelPort(List.of(TOOL_CALL, answer)),
                promptTemplatePort,
                toolPort,
                new FrameworkRuntimeConfig("test-model", 0.0, 128, 5));

        FrameworkAgentResult result = graph.run("task-1", "查询患者 P001");

        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.answer()).isEqualTo("患者张三，62 岁");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).stepNo()).isEqualTo(1);
        assertThat(result.steps().get(0).toolName()).isEqualTo("PatientLookupTool");
        assertThat(result.steps().get(0).observation()).contains("张三");
        assertThat(result.usage().totalTokens()).isEqualTo(17);
        assertThat(result.model()).isEqualTo("test-model");
    }

    @Test
    void failsWhenLoopExceedsMaxIterations() {
        FrameworkAgentGraph graph = new FrameworkAgentGraph(
                new ScriptedChatModelPort(List.of(TOOL_CALL, TOOL_CALL, TOOL_CALL, TOOL_CALL)),
                promptTemplatePort,
                toolPort,
                new FrameworkRuntimeConfig("test-model", 0.0, 128, 2));

        assertThatThrownBy(() -> graph.run("task-1", "查询患者 P001"))
                .isInstanceOf(AgentLoopExceededException.class);
    }

    @Test
    void failsWhenAnswerIsEmpty() {
        ChatResult blank = new ChatResult("   ", List.of(), FinishReason.STOP, TokenUsage.unknown(), "test-model");
        FrameworkAgentGraph graph = new FrameworkAgentGraph(
                new ScriptedChatModelPort(List.of(blank)),
                promptTemplatePort,
                toolPort,
                new FrameworkRuntimeConfig("test-model", 0.0, 128, 5));

        assertThatThrownBy(() -> graph.run("task-1", "查询患者 P001"))
                .isInstanceOf(AgentExecutionException.class);
    }

    /**
     * 脚本化模型端口：按顺序返回预设响应，超出后重复最后一个。
     */
    private static final class ScriptedChatModelPort implements ChatModelPort {

        private final List<ChatResult> responses;
        private int index = 0;

        private ScriptedChatModelPort(List<ChatResult> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            ChatResult response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }
}
