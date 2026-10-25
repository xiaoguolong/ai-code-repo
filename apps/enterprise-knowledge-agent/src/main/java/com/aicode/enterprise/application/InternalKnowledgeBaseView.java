package com.aicode.enterprise.application;

/**
 * 内部知识库视图（Week 25）。
 *
 * @param knowledgeBaseId 知识库 ID
 * @param name            名称
 * @param description     描述
 * @param documentCount   文档数
 */
public record InternalKnowledgeBaseView(
        long knowledgeBaseId,
        String name,
        String description,
        int documentCount
) {
}
