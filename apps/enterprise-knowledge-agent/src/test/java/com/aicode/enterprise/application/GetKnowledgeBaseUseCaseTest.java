package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 知识库归属校验用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GetKnowledgeBaseUseCaseTest {

    @Mock
    private KnowledgeBasePort knowledgeBasePort;

    private GetKnowledgeBaseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetKnowledgeBaseUseCase(knowledgeBasePort);
    }

    @Test
    void forbidsCrossTenantAccess() {
        KnowledgeBase kb = new KnowledgeBase(1L, 2L, "他人知识库", null, Instant.now());
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(kb));

        assertThatThrownBy(() -> useCase.get(1L, 1L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void returnsOwnKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(1L, 1L, "我的知识库", null, Instant.now());
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(kb));

        assertThat(useCase.get(1L, 1L).name()).isEqualTo("我的知识库");
    }

    @Test
    void throwsNotFoundWhenMissing() {
        when(knowledgeBasePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(1L, 99L)).isInstanceOf(NotFoundException.class);
    }
}
