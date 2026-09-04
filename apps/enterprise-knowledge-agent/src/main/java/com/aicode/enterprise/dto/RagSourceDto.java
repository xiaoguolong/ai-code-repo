package com.aicode.enterprise.dto;

/**
 * 知识库问答检索来源项。
 */
public record RagSourceDto(int chunkIndex, String content, double score) {
}
