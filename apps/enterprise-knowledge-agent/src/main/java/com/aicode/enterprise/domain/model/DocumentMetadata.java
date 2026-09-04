package com.aicode.enterprise.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 文档元数据。不含正文与向量，只描述一个已入库知识文档及其归属知识库。
 */
public record DocumentMetadata(
        String documentId,
        Long knowledgeBaseId,
        String name,
        int chunkCount,
        Instant createdAt
) {

    public DocumentMetadata {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
