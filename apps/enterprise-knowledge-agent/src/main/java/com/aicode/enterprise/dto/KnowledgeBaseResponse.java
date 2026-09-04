package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.KnowledgeBase;

import java.time.Instant;

/**
 * 知识库响应体。
 */
public record KnowledgeBaseResponse(Long id, String name, String description, Instant createdAt) {

    /**
     * 从领域模型转换。
     */
    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.id(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.createdAt()
        );
    }
}
