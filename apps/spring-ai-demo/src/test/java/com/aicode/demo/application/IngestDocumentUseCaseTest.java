package com.aicode.demo.application;

import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.DocumentMetadata;
import com.aicode.demo.domain.port.DocumentPort;
import com.aicode.demo.domain.port.EmbeddingModelPort;
import com.aicode.demo.infrastructure.vector.InMemoryVectorStoreAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档入库用例：解析切片 → 向量化 → 入库 → 建文档元数据。
 */
@ExtendWith(MockitoExtension.class)
class IngestDocumentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Mock
    private EmbeddingModelPort embedding;
    @Mock
    private DocumentPort documentPort;

    private InMemoryVectorStoreAdapter vectorStore;
    private IngestDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStoreAdapter();
        useCase = new IngestDocumentUseCase(
                embedding,
                vectorStore,
                documentPort,
                new RagRuntimeConfig(4, 10, 0),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldChunkEmbedAndStore_eachChunk() {
        when(embedding.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f});

        IngestDocumentOutcome outcome = useCase.ingest(new IngestDocumentCommand("知识库文档", "0123456789abcdefghij"));

        assertThat(outcome.documentId()).isNotBlank();
        assertThat(outcome.name()).isEqualTo("知识库文档");
        assertThat(outcome.chunkCount()).isEqualTo(2);

        verify(embedding, times(2)).embed(anyString());
        verify(documentPort).create(any(DocumentMetadata.class));
        assertThat(vectorStore.search(new float[]{1f, 0f, 0f}, 10)).hasSize(2);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> useCase.ingest(new IngestDocumentCommand("   ", "content")))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> useCase.ingest(new IngestDocumentCommand("doc", "  ")))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void shouldCreateDocumentBeforeStoringChunks() {
        when(embedding.embed(anyString())).thenReturn(new float[]{1f});

        useCase.ingest(new IngestDocumentCommand("doc", "hello"));

        verify(documentPort).create(any(DocumentMetadata.class));
    }
}
