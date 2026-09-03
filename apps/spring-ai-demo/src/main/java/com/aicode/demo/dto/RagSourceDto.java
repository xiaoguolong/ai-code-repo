package com.aicode.demo.dto;

/**
 * RAG 检索来源项。
 */
public record RagSourceDto(int chunkIndex, String content, double score) {
}
