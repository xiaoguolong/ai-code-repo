package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 运行配置。
 *
 * @param vectorStore    memory 或 pgvector，默认 memory
 * @param defaultTopK   默认检索条数，默认 4
 * @param chunkSize     切片窗口字符数，默认 500
 * @param chunkOverlap  切片重叠字符数，默认 50
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String vectorStore,
        Integer defaultTopK,
        Integer chunkSize,
        Integer chunkOverlap
) {

    /**
     * @return 有效默认 topK，缺省或非法时回退 4
     */
    public int resolvedDefaultTopK() {
        return defaultTopK == null || defaultTopK <= 0 ? 4 : defaultTopK;
    }

    /**
     * @return 有效切片大小，缺省或非法时回退 500
     */
    public int resolvedChunkSize() {
        return chunkSize == null || chunkSize <= 0 ? 500 : chunkSize;
    }

    /**
     * @return 有效重叠字符数，缺省或非法时回退 50
     */
    public int resolvedChunkOverlap() {
        return chunkOverlap == null || chunkOverlap < 0 ? 50 : chunkOverlap;
    }
}
