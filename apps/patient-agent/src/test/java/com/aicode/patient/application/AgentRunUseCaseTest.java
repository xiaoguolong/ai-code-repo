package com.aicode.patient.application;

import com.aicode.patient.domain.ReActAgent;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import com.aicode.core.domain.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Agent 运行用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunUseCaseTest {

    @Mock
    private ReActAgent reactAgent;

    private AgentRunUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AgentRunUseCase(reactAgent);
    }

    @Test
    void rejectsBlankTask() {
        assertThatThrownBy(() -> useCase.run("   "))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void runsAgentAndReturnsResultWithTaskId() {
        when(reactAgent.run(any()))
                .thenReturn(new AgentResult("task-1", "答案", List.of(), 0, TokenUsage.unknown(), "deepseek-chat"));

        AgentResult result = useCase.run("评估患者");

        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.answer()).isEqualTo("答案");
    }

    @Test
    void generatesFreshTaskIdWhenAgentReturnsNullTaskId() {
        when(reactAgent.run(any(AgentTask.class)))
                .thenAnswer(invocation -> new AgentResult(
                        invocation.getArgument(0, AgentTask.class).taskId(),
                        "答案", List.of(), 0, TokenUsage.unknown(), "deepseek-chat"));

        AgentResult result = useCase.run("评估患者");

        assertThat(result.taskId()).isNotBlank();
    }
}
