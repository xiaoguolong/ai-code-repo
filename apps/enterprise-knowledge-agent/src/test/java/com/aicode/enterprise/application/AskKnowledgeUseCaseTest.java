package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.ChatResult;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.model.PromptTemplate;
import com.aicode.enterprise.domain.model.TokenUsage;
import com.aicode.enterprise.domain.model.VectorSearchHit;
import com.aicode.enterprise.domain.port.AuditPort;
import com.aicode.enterprise.domain.port.ChatModelPort;
import com.aicode.enterprise.domain.port.ConversationPort;
import com.aicode.enterprise.domain.port.EmbeddingModelPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.domain.port.PromptTemplatePort;
import com.aicode.enterprise.domain.port.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库问答用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AskKnowledgeUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private KnowledgeBasePort knowledgeBasePort;

    @Mock
    private EmbeddingModelPort embeddingModelPort;

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private ChatModelPort chatModelPort;

    @Mock
    private PromptTemplatePort promptTemplatePort;

    @Mock
    private ConversationPort conversationPort;

    @Mock
    private AuditPort auditPort;

    private AskKnowledgeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AskKnowledgeUseCase(
                knowledgeBasePort,
                embeddingModelPort,
                vectorStorePort,
                chatModelPort,
                promptTemplatePort,
                conversationPort,
                auditPort,
                new RagRuntimeConfig(4, 500, 50),
                new ChatRuntimeConfig("deepseek-chat", 0.7, 1024),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void answersAndRecordsHistoryAndToken() {
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(new KnowledgeBase(1L, 1L, "kb", null, NOW)));
        when(embeddingModelPort.embed(any())).thenReturn(new float[]{1f, 0f});
        when(vectorStorePort.search(any(), eq(4), eq(1L)))
                .thenReturn(List.of(new VectorSearchHit("doc-1", 1L, 0, "片段", 0.9)));
        when(promptTemplatePort.load("rag")).thenReturn(new PromptTemplate("v1", "系统提示"));
        when(chatModelPort.chat(any(), any()))
                .thenReturn(new ChatResult("答案", new TokenUsage(10, 20, 30), "deepseek-chat"));

        KnowledgeAnswer answer = useCase.ask(new KnowledgeQuestion(1L, 1L, "sess-1", "问题？", null));

        assertThat(answer.answer()).isEqualTo("答案");
        assertThat(answer.sessionId()).isEqualTo("sess-1");
        assertThat(answer.sources()).hasSize(1);
        verify(conversationPort).ensureSession(eq("sess-1"), eq(1L), eq(1L), any());
        verify(auditPort).record(any());
    }

    @Test
    void forbidsAskingOthersKnowledgeBase() {
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(new KnowledgeBase(1L, 2L, "kb", null, NOW)));

        assertThatThrownBy(() -> useCase.ask(new KnowledgeQuestion(1L, 1L, null, "问题？", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> useCase.ask(new KnowledgeQuestion(1L, 1L, null, "  ", null)))
                .isInstanceOf(InvalidChatRequestException.class);
    }
}
