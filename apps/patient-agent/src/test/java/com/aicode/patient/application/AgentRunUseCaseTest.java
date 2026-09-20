package com.aicode.patient.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.LongTermMemoryPort;
import com.aicode.core.domain.port.MemoryPort;
import com.aicode.patient.domain.ReActAgent;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent 运行用例记忆编排单元测试：短期加载/截断/写回，长期召回/保存。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunUseCaseTest {

    @Mock
    private ReActAgent reactAgent;

    @Mock
    private MemoryPort memoryPort;

    @Mock
    private LongTermMemoryPort longTermMemoryPort;

    private AgentRunUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AgentRunUseCase(
                reactAgent,
                memoryPort,
                longTermMemoryPort,
                new AgentRuntimeConfig("deepseek-chat", 0.7, 1024, 5, 2, 3)
        );
    }

    private void stubDefaults() {
        when(memoryPort.load(anyString())).thenReturn(List.of());
        when(longTermMemoryPort.search(anyString(), anyInt())).thenReturn(List.of());
        when(reactAgent.run(any(), any(), any())).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            return new AgentResult(task.taskId(), task.sessionId(), "答案", List.of(), 0, 0,
                    TokenUsage.unknown(), "deepseek-chat");
        });
    }

    @Test
    void rejectsBlankTask() {
        assertThatThrownBy(() -> useCase.run("s1", "   "))
                .isInstanceOf(InvalidChatRequestException.class);
        verifyNoInteractions(memoryPort, longTermMemoryPort, reactAgent);
    }

    @Test
    void generatesSessionIdWhenMissing() {
        stubDefaults();

        AgentResult result = useCase.run(null, "评估患者");

        assertThat(result.sessionId()).isNotBlank();
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(memoryPort).append(sessionCaptor.capture(), any());
        assertThat(sessionCaptor.getValue()).isEqualTo(result.sessionId());
    }

    @Test
    void trimsHistoryAndPassesRecalledMemoryThenPersistsBoth() {
        stubDefaults();
        List<ChatMessage> history = IntStream.range(0, 5)
                .mapToObj(i -> new ChatMessage(i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT, "h" + i))
                .toList();
        when(memoryPort.load("s1")).thenReturn(history);
        MemoryHit hit = new MemoryHit(new MemoryRecord("m1", "s0", "历史 任务", "历史 答案", null), 0.8);
        when(longTermMemoryPort.search("评估患者", 3)).thenReturn(List.of(hit));

        useCase.run("s1", "评估患者");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryHit>> hitCaptor = ArgumentCaptor.forClass(List.class);
        verify(reactAgent).run(any(), historyCaptor.capture(), hitCaptor.capture());

        assertThat(historyCaptor.getValue()).hasSize(2);
        assertThat(historyCaptor.getValue()).extracting(ChatMessage::content).containsExactly("h3", "h4");
        assertThat(hitCaptor.getValue()).containsExactly(hit);
        verify(memoryPort).append(eq("s1"), any());
        verify(longTermMemoryPort).save(any());
    }

    @Test
    void persistsShortTermEvenWithoutLongTermHit() {
        stubDefaults();

        useCase.run("s1", "评估患者");

        verify(memoryPort).append(eq("s1"), any());
        verify(longTermMemoryPort).save(any());
    }
}
