package com.aicode.patient.domain;

import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.exception.AgentExecutionException;
import com.aicode.patient.domain.exception.AgentLoopExceededException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReActAgent 原生 Function Calling 循环单元测试。模型与工具用桩，不连真实网络。
 */
@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Mock
    private ChatModelPort chatModelPort;

    @Mock
    private PromptTemplatePort promptTemplatePort;

    @Mock
    private ToolPort toolPort;

    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        when(promptTemplatePort.load("agent")).thenReturn(new PromptTemplate("v1", "系统提示"));
        when(toolPort.definitions()).thenReturn(List.of(
                new ToolDefinition("PatientTool", "查询患者", Map.of("type", "object"))
        ));
        agent = new ReActAgent(
                chatModelPort,
                promptTemplatePort,
                toolPort,
                new AgentRuntimeConfig("deepseek-chat", 0.7, 1024, 5)
        );
    }

    @Test
    void executesToolThenReturnsFinalAnswerWithTrace() {
        when(toolPort.execute(any())).thenReturn(new ToolResult("{\"id\":\"P001\",\"name\":\"张三\"}"));
        when(chatModelPort.chat(any(), any(), any()))
                .thenReturn(
                        new ChatResult(
                                null,
                                List.of(new ToolCall("call-1", "PatientTool", "{\"patientId\":\"P001\"}")),
                                FinishReason.TOOL_CALLS,
                                new TokenUsage(10, 20, 30),
                                "deepseek-chat"),
                        new ChatResult(
                                "建议随访",
                                List.of(),
                                FinishReason.STOP,
                                new TokenUsage(5, 5, 10),
                                "deepseek-chat")
                );

        AgentResult result = agent.run(new AgentTask("task-1", "评估患者 P001 的出院风险"));

        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.answer()).isEqualTo("建议随访");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).toolName()).isEqualTo("PatientTool");
        assertThat(result.totalSteps()).isEqualTo(1);
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.totalUsage().promptTokens()).isEqualTo(15);
        assertThat(result.totalUsage().completionTokens()).isEqualTo(25);
        assertThat(result.totalUsage().totalTokens()).isEqualTo(40);
    }

    @Test
    void throwsWhenMaxIterationsExceededWithRepeatedToolCalls() {
        when(toolPort.execute(any())).thenReturn(new ToolResult("{\"id\":\"P001\",\"name\":\"张三\"}"));
        when(chatModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResult(
                        null,
                        List.of(new ToolCall("call-1", "PatientTool", "{\"patientId\":\"P001\"}")),
                        FinishReason.TOOL_CALLS,
                        new TokenUsage(1, 1, 2),
                        "deepseek-chat"));

        assertThatThrownBy(() -> agent.run(new AgentTask("task-1", "复杂任务")))
                .isInstanceOf(AgentLoopExceededException.class);
    }

    @Test
    void throwsWhenModelProducesEmptyOutputWithoutToolCalls() {
        when(chatModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResult("   ", List.of(), FinishReason.STOP, TokenUsage.unknown(), "deepseek-chat"));

        assertThatThrownBy(() -> agent.run(new AgentTask("task-1", "任务")))
                .isInstanceOf(AgentExecutionException.class);
    }
}
