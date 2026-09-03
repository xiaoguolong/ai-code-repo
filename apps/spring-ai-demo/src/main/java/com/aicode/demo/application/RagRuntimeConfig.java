package com.aicode.demo.application;

/**
 * RAG 用例运行时配置。由基础设施把配置文件映射进来，领域不读 Environment。
 *
 * @param defaultTopK  默认检索条数
 * @param chunkSize    切片窗口字符数
 * @param chunkOverlap 切片重叠字符数
 */
public record RagRuntimeConfig(int defaultTopK, int chunkSize, int chunkOverlap) {
}
