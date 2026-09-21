package com.aicode.framework.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.domain.FrameworkAgentGraph;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Graph Agent 用例测试：任务校验与领域服务委派。
 */
@ExtendWith(MockitoExtension.class)
class FrameworkAgentUseCaseTest {

    @Mock
    private FrameworkAgentGraph frameworkAgentGraph;

    @Test
    void rejectsBlankTask() {
        FrameworkAgentUseCase useCase = new FrameworkAgentUseCase(frameworkAgentGraph);

        assertThatThrownBy(() -> useCase.run("   "))
                .isInstanceOf(InvalidChatRequestException.class);
        verifyNoInteractions(frameworkAgentGraph);
    }

    @Test
    void delegatesTrimmedTaskToGraph() {
        when(frameworkAgentGraph.run(anyString(), anyString()))
                .thenReturn(new FrameworkAgentResult("task-1", "答案", List.of(), 0,
                        TokenUsage.unknown(), "test-model"));
        FrameworkAgentUseCase useCase = new FrameworkAgentUseCase(frameworkAgentGraph);

        FrameworkAgentResult result = useCase.run("  查询患者  ");

        assertThat(result.answer()).isEqualTo("答案");
    }
}
