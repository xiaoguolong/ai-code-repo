package com.aicode.framework.knowledge.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.knowledge.domain.KnowledgeQaAgent;
import com.aicode.framework.knowledge.domain.model.KnowledgeAnswer;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryKnowledgeBindingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库问答用例（Week 25）：绑定校验先于模型调用、入参校验、无平台范围拒绝。
 */
class KnowledgeQaUseCaseTest {

    private static final long KB_ID = 11L;
    private static final long USER_ID = 2L;

    private InMemoryKnowledgeBindingAdapter bindingPort;
    private KnowledgeQaAgent agent;
    private PlatformKnowledgeProperties properties;
    private KnowledgeQaUseCase useCase;

    @BeforeEach
    void setUp() {
        bindingPort = new InMemoryKnowledgeBindingAdapter();
        agent = mock(KnowledgeQaAgent.class);
        properties = new PlatformKnowledgeProperties();
        PlatformRunScopePort scopePort = () -> Optional.of(new PlatformMemoryScope("default", USER_ID));
        useCase = new KnowledgeQaUseCase(bindingPort, scopePort, agent, properties);
    }

    @Test
    void runsAgentWhenKnowledgeBaseIsBound() {
        bindingPort.save(binding(true));
        when(agent.run(KB_ID, "糖尿病随访要点")).thenReturn(answer());

        KnowledgeAnswer result = useCase.run(USER_ID, KB_ID, "  糖尿病随访要点  ");

        assertThat(result.answer()).isEqualTo("答案");
        verify(agent).run(KB_ID, "糖尿病随访要点");
    }

    @Test
    void unboundKnowledgeBaseIsNotFoundBeforeCallingModel() {
        assertThatThrownBy(() -> useCase.run(USER_ID, KB_ID, "q"))
                .isInstanceOf(PlatformNotFoundException.class);
        verifyNoInteractions(agent);
    }

    @Test
    void disabledBindingIsNotFound() {
        bindingPort.save(binding(false));

        assertThatThrownBy(() -> useCase.run(USER_ID, KB_ID, "q"))
                .isInstanceOf(PlatformNotFoundException.class);
    }

    @Test
    void missingBindingIsNotFoundBeforeCallingModel() {
        bindingPort.save(binding(true).markMissing(Instant.parse("2026-10-26T00:00:00Z")));

        assertThatThrownBy(() -> useCase.run(USER_ID, KB_ID, "q"))
                .isInstanceOf(PlatformNotFoundException.class)
                .hasMessageContaining("removed on enterprise side");
        verifyNoInteractions(agent);
    }

    @Test
    void validatesInputs() {
        assertThatThrownBy(() -> useCase.run(USER_ID, null, "q"))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> useCase.run(USER_ID, 0L, "q"))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> useCase.run(USER_ID, KB_ID, "   "))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void rejectsOverlongQuestion() {
        properties.setMaxQuestionChars(10);
        KnowledgeQaUseCase limited = new KnowledgeQaUseCase(
                bindingPort, () -> Optional.of(new PlatformMemoryScope("default", USER_ID)), agent, properties);

        assertThatThrownBy(() -> limited.run(USER_ID, KB_ID, "x".repeat(11)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void requiresPlatformRunScope() {
        KnowledgeQaUseCase noScope = new KnowledgeQaUseCase(
                bindingPort, PlatformRunScopePort.NONE, agent, properties);

        assertThatThrownBy(() -> noScope.run(USER_ID, KB_ID, "q"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(agent);
    }

    private KnowledgeBinding binding(boolean enabled) {
        Instant now = Instant.parse("2026-10-25T10:00:00Z");
        return new KnowledgeBinding("b-1", "default", 7L, KB_ID, "慢病指南", "内部", enabled, 1L, now, now,
                KnowledgeBindingSyncStatus.ACTIVE, now);
    }

    private KnowledgeAnswer answer() {
        return new KnowledgeAnswer(
                KB_ID, "慢病指南", "糖尿病随访要点", "答案", List.of(), 1, false,
                TokenUsage.unknown(), "test-model", List.of());
    }
}
