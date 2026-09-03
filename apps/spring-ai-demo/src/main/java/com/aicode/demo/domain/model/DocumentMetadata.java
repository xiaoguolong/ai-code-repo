package com.aicode.demo.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 文档元数据。不含正文与向量，只描述一个已入库知识文档。
 */
public record DocumentMetadata(String documentId, String name, Instant createdAt) {

    public DocumentMetadata {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
