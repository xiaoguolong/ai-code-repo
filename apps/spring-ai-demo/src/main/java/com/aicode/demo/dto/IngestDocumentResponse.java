package com.aicode.demo.dto;

import com.aicode.demo.application.IngestDocumentOutcome;

/**
 * 文档入库成功体。
 */
public record IngestDocumentResponse(String documentId, String name, int chunkCount) {

    /**
     * 从用例出参转换，隔离应用层类型。
     */
    public static IngestDocumentResponse from(IngestDocumentOutcome outcome) {
        return new IngestDocumentResponse(outcome.documentId(), outcome.name(), outcome.chunkCount());
    }
}
