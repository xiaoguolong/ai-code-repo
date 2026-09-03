package com.aicode.demo.dto;

import com.aicode.demo.domain.model.DocumentMetadata;

import java.util.List;

/**
 * 文档列表响应体。
 */
public record DocumentListResponse(List<DocumentItemDto> documents) {

    /**
     * 从领域元数据列表转换。
     */
    public static DocumentListResponse from(List<DocumentMetadata> metadata) {
        return new DocumentListResponse(metadata.stream().map(DocumentItemDto::from).toList());
    }
}
