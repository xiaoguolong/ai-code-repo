package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.DocumentMetadata;

import java.time.Instant;

/**
 * 文档列表项。
 */
public record DocumentItemDto(String documentId, String name, int chunkCount, Instant createdAt) {

    /**
     * 从领域模型转换。
     */
    public static DocumentItemDto from(DocumentMetadata metadata) {
        return new DocumentItemDto(metadata.documentId(), metadata.name(), metadata.chunkCount(), metadata.createdAt());
    }
}
