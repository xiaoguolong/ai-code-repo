package com.aicode.enterprise.application;

import java.util.List;

/**
 * 内部检索结果（Week 25）。
 *
 * @param enterpriseUserId 执行检索的本地用户 ID
 * @param knowledgeBase    命中的知识库
 * @param hits             切片命中（按相似度倒序）
 */
public record InternalKnowledgeResult(
        long enterpriseUserId,
        InternalKnowledgeBaseView knowledgeBase,
        List<InternalKnowledgeHit> hits
) {

    public InternalKnowledgeResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
