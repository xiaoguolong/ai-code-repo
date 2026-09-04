package com.aicode.enterprise.application;

/**
 * 文档入库用例出参。chunkCount 为本次切片并向量化的块数。
 */
public record IngestDocumentOutcome(String documentId, String name, int chunkCount) {
}
