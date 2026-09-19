package com.aicode.core.domain.model;

import java.util.Objects;

/**
 * 向量检索命中项。score 为余弦相似度（越高越相关），范围通常 [0,1]。
 */
public record VectorSearchHit(
        String documentId,
        Long knowledgeBaseId,
        int chunkIndex,
        String content,
        double score
) {

    public VectorSearchHit {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        Objects.requireNonNull(content, "content");
    }
}
