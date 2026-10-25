package com.aicode.enterprise.application;

import java.util.List;

/**
 * 内部知识库清单（Week 25）。
 *
 * <p>平台侧「导入知识库」用它把某个平台用户映射到的企业用户的知识库登记到租户下，
 * 并把 {@code enterpriseUserId} 一起存进绑定表（后续检索不再依赖 SSO 映射）。</p>
 *
 * @param enterpriseUserId 本地用户 ID
 * @param bases            该用户的知识库
 */
public record InternalKnowledgeCatalog(long enterpriseUserId, List<InternalKnowledgeBaseView> bases) {

    public InternalKnowledgeCatalog {
        bases = bases == null ? List.of() : List.copyOf(bases);
    }
}
