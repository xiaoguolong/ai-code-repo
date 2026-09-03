package com.aicode.demo.application;

import com.aicode.demo.domain.TextChunker;
import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.DocumentMetadata;
import com.aicode.demo.domain.port.DocumentPort;
import com.aicode.demo.domain.port.EmbeddingModelPort;
import com.aicode.demo.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 文档入库用例：建元数据 → 切片 → 向量化 → 入库。文档先于切片创建，避免外键悬空。
 */
@Service
public class IngestDocumentUseCase {

    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentPort documentPort;
    private final RagRuntimeConfig config;
    private final Clock clock;

    public IngestDocumentUseCase(
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            DocumentPort documentPort,
            RagRuntimeConfig config,
            Clock clock
    ) {
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.documentPort = documentPort;
        this.config = config;
        this.clock = clock;
    }

    /**
     * 解析并入库一个文档。
     *
     * @param command 文档名称与正文
     * @return 文档标识与切片数
     * @throws InvalidChatRequestException 名称或正文空白
     */
    public IngestDocumentOutcome ingest(IngestDocumentCommand command) {
        String name = command.name() == null ? "" : command.name().trim();
        String content = command.content() == null ? "" : command.content().trim();
        if (name.isEmpty()) {
            throw new InvalidChatRequestException("name must not be blank");
        }
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("content must not be blank");
        }
        String documentId = UUID.randomUUID().toString();
        documentPort.create(new DocumentMetadata(documentId, name, clock.instant()));
        List<String> texts = TextChunker.split(content, config.chunkSize(), config.chunkOverlap());
        for (int index = 0; index < texts.size(); index++) {
            DocumentChunk chunk = new DocumentChunk(documentId, index, texts.get(index));
            float[] vector = embeddingModelPort.embed(texts.get(index));
            vectorStorePort.put(chunk, vector);
        }
        return new IngestDocumentOutcome(documentId, name, texts.size());
    }
}
