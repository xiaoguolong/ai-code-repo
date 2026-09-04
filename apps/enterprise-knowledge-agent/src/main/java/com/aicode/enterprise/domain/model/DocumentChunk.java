package com.aicode.enterprise.domain.model;

import java.util.Objects;

/**
 * 文档切片。切片文本与其归属文档、知识库、序号绑定，供向量库检索后回溯来源。
 */
public record DocumentChunk(String documentId, Long knowledgeBaseId, int chunkIndex, String content) {

    public DocumentChunk {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        Objects.requireNonNull(content, "content");
    }
}
