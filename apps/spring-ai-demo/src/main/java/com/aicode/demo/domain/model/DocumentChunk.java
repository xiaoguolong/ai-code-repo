package com.aicode.demo.domain.model;

import java.util.Objects;

/**
 * 文档切片。切片文本与其归属文档、序号绑定，供向量库检索后回溯来源。
 */
public record DocumentChunk(String documentId, int chunkIndex, String content) {

    public DocumentChunk {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(content, "content");
    }
}
