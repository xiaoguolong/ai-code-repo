package com.aicode.enterprise.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 知识库。归属某个用户，包含多篇文档。多租户隔离的基本单位。
 */
public record KnowledgeBase(Long id, Long userId, String name, String description, Instant createdAt) {

    public KnowledgeBase {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
