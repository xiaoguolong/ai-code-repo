package com.aicode.framework.knowledge.domain.model;

import java.util.List;

/**
 * enterprise 侧某用户的全部知识库（Week 25，导入用）。
 *
 * @param enterpriseUserId 本地用户 ID
 * @param bases            知识库清单
 */
public record KnowledgeBaseCatalog(long enterpriseUserId, List<KnowledgeBaseCatalogEntry> bases) {

    public KnowledgeBaseCatalog {
        bases = bases == null ? List.of() : List.copyOf(bases);
    }
}
