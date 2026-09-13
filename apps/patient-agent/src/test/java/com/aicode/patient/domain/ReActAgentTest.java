package com.aicode.patient.domain;

import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.exception.AgentExecutionException;
import com.aicode.patient.domain.exception.AgentLoopExceededException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import com.aicode.patient.domain.model.ChatResult;
import com.aicode.patient.domain.model.PromptTemplate;
import com.aicode.patient.domain.model.TokenUsage;
import com.aicode.patient.domain.port.ChatModelPort;
import com.aicode.patient.domain.port.PromptTemplatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReActAgent 循环单元测试。模型用桩脚本，不连真实网络。
 */
@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Mock
    private ChatModelPort chatModelPort;

    @Mock
    private PromptTemplatePort promptTemplatePort;

    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        when(promptTemplatePort.load("agent")).thenReturn(new PromptTemplate("v1", "系统提示"));
        agent = new ReActAgent(
                chatModelPort,
                promptTemplatePort,
                new ReActOutputParser(),
                new AgentRuntimeConfig("deepseek-chat", 0.7, 1024, 5)
        );
    }

    @Test
    void runsLoopAndReturnsFinalAnswerWithTrace() {
        when(chatModelPort.chat(any(), any()))
                .thenReturn(
                        new ChatResult(
                                "Thought: 收集信息\nAction: 整理\nObservation: 已获得指标",
                                new TokenUsage(10, 20, 30),
                                "deepseek-chat"),
                        new ChatResult(
                                "Thought: 评估风险\nAction: 计算\nObservation: 中等\nFinal Answer: 建议随访",
                                new TokenUsage(5, 5, 10),
                                "deepseek-chat")
                );

        AgentResult result = agent.run(new AgentTask("task-1", "评估患者风险"));

        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.answer()).isEqualTo("建议随访");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.totalSteps()).isEqualTo(2);
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.totalUsage().promptTokens()).isEqualTo(15);
        assertThat(result.totalUsage().completionTokens()).isEqualTo(25);
        assertThat(result.totalUsage().totalTokens()).isEqualTo(40);
    }

    @Test
    void throwsWhenMaxIterationsExceeded() {
        when(chatModelPort.chat(any(), any()))
                .thenReturn(new ChatResult(
                        "Thought: t\nAction: a\nObservation: o",
                        new TokenUsage(1, 1, 2),
                        "deepseek-chat"));

        assertThatThrownBy(() -> agent.run(new AgentTask("task-1", "复杂任务")))
                .isInstanceOf(AgentLoopExceededException.class);
    }

    @Test
    void throwsWhenModelProducesEmptyOutput() {
        when(chatModelPort.chat(any(), any()))
                .thenReturn(new ChatResult("   ", TokenUsage.unknown(), "deepseek-chat"));

        assertThatThrownBy(() -> agent.run(new AgentTask("task-1", "任务")))
                .isInstanceOf(AgentExecutionException.class);
    }
}
