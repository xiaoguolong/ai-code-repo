package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.TextChunker;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.DocumentChunk;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.EmbeddingModelPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 文档入库用例：校验知识库归属 → 建元数据 → 切片 → 向量化 → 入库。
 */
@Service
public class IngestDocumentUseCase {

    private final KnowledgeBasePort knowledgeBasePort;
    private final DocumentPort documentPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final RagRuntimeConfig config;
    private final Clock clock;

    public IngestDocumentUseCase(
            KnowledgeBasePort knowledgeBasePort,
            DocumentPort documentPort,
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            RagRuntimeConfig config,
            Clock clock
    ) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.documentPort = documentPort;
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.config = config;
        this.clock = clock;
    }

    /**
     * @throws InvalidChatRequestException 名称或正文空白
     * @throws NotFoundException           知识库不存在
     * @throws ForbiddenException          知识库不属于当前用户
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
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(command.knowledgeBaseId())
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (!knowledgeBase.userId().equals(command.userId())) {
            throw new ForbiddenException("access denied");
        }
        Long knowledgeBaseId = command.knowledgeBaseId();
        String documentId = UUID.randomUUID().toString();
        List<String> chunks = TextChunker.split(content, config.chunkSize(), config.chunkOverlap());
        documentPort.create(new DocumentMetadata(documentId, knowledgeBaseId, name, chunks.size(), clock.instant()));
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = new DocumentChunk(documentId, knowledgeBaseId, index, chunks.get(index));
            float[] vector = embeddingModelPort.embed(chunks.get(index));
            vectorStorePort.put(chunk, vector);
        }
        return new IngestDocumentOutcome(documentId, name, chunks.size());
    }
}
