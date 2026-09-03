package com.aicode.demo.dto;

import com.aicode.demo.domain.model.DocumentMetadata;

import java.time.Instant;

/**
 * 文档列表项。
 */
public record DocumentItemDto(String documentId, String name, Instant createdAt) {

    /**
     * 从领域元数据转换。
     */
    public static DocumentItemDto from(DocumentMetadata metadata) {
        return new DocumentItemDto(metadata.documentId(), metadata.name(), metadata.createdAt());
    }
}
