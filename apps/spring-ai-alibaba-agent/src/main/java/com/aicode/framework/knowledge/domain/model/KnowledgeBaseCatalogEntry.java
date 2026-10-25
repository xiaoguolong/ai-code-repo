package com.aicode.framework.knowledge.domain.model;

/**
 * 平台可见的知识库条目（Week 25，导入时来自 enterprise）。
 *
 * @param knowledgeBaseId 知识库 ID
 * @param name            名称
 * @param description     描述
 * @param documentCount   文档数
 */
public record KnowledgeBaseCatalogEntry(
        long knowledgeBaseId,
        String name,
        String description,
        int documentCount
) {
}
