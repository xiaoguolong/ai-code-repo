package com.aicode.demo.domain.model;

import java.util.Objects;

/**
 * 向量检索命中项。score 为余弦相似度（越高越相关），范围通常 [0,1]。
 */
public record VectorSearchHit(String documentId, int chunkIndex, String content, double score) {

    public VectorSearchHit {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(content, "content");
    }
}
