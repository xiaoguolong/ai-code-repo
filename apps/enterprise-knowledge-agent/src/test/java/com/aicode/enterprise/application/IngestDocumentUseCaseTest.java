package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.core.infrastructure.vector.InMemoryVectorStoreAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档入库用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class IngestDocumentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private KnowledgeBasePort knowledgeBasePort;

    @Mock
    private DocumentPort documentPort;

    @Mock
    private EmbeddingModelPort embeddingModelPort;

    private InMemoryVectorStoreAdapter vectorStorePort;
    private IngestDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        vectorStorePort = new InMemoryVectorStoreAdapter();
        RagRuntimeConfig config = new RagRuntimeConfig(4, 500, 50);
        useCase = new IngestDocumentUseCase(
                knowledgeBasePort, documentPort, embeddingModelPort, vectorStorePort, config, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void ingestsAndVectorsEachChunk() {
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(new KnowledgeBase(1L, 1L, "kb", null, NOW)));
        when(embeddingModelPort.embed(any())).thenReturn(new float[]{1f, 0f});

        IngestDocumentOutcome outcome = useCase.ingest(new IngestDocumentCommand(1L, 1L, "doc", "这是一段测试文本"));

        assertThat(outcome.documentId()).isNotBlank();
        assertThat(outcome.chunkCount()).isGreaterThan(0);
        verify(documentPort).create(any());
        assertThat(vectorStorePort.search(new float[]{1f, 0f}, 10, 1L)).isNotEmpty();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> useCase.ingest(new IngestDocumentCommand(1L, 1L, "  ", "content")))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void forbidsIngestIntoOthersKnowledgeBase() {
        when(knowledgeBasePort.findById(1L)).thenReturn(Optional.of(new KnowledgeBase(1L, 2L, "kb", null, NOW)));

        assertThatThrownBy(() -> useCase.ingest(new IngestDocumentCommand(1L, 1L, "doc", "content")))
                .isInstanceOf(ForbiddenException.class);
    }
}
