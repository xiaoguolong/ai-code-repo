package com.aicode.framework.knowledge.domain.model;

/**
 * 知识库检索查询（Week 25）。
 *
 * @param enterpriseUserId 执行检索的 enterprise 本地用户（知识库归属人，来自绑定）
 * @param knowledgeBaseId  知识库 ID
 * @param query            问题
 * @param topK             条数
 */
public record KnowledgeSearchQuery(long enterpriseUserId, long knowledgeBaseId, String query, int topK) {
}
