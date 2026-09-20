package com.aicode.core.domain.model;

import java.util.Objects;

/**
 * 长期记忆语义检索命中项。score 为余弦相似度（越高越相关），仅返回正相关命中。
 */
public record MemoryHit(MemoryRecord record, double score) {

    public MemoryHit {
        Objects.requireNonNull(record, "record");
    }
}
