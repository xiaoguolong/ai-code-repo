package com.aicode.framework.knowledge.domain.model;

/**
 * 知识库检索命中（Week 25）。
 *
 * @param knowledgeBaseId 知识库 ID
 * @param documentId      文档业务 ID
 * @param documentName    文档名
 * @param chunkIndex      切片序号
 * @param content         切片正文
 * @param score           相似度
 */
public record KnowledgeSearchHit(
        long knowledgeBaseId,
        String documentId,
        String documentName,
        int chunkIndex,
        String content,
        double score
) {
}
