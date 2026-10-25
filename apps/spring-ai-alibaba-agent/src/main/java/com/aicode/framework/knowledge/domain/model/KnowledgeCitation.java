package com.aicode.framework.knowledge.domain.model;

/**
 * 答案引用片段（Week 25）。
 *
 * @param documentId   文档 ID
 * @param documentName 文档名
 * @param chunkIndex   切片序号
 * @param content      正文（平台输出脱敏后）
 * @param score        相似度
 */
public record KnowledgeCitation(
        String documentId,
        String documentName,
        int chunkIndex,
        String content,
        double score
) {
}
