package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量化模型接入配置。apiKey 只允许来自环境变量，禁止写入仓库。
 *
 * @param provider       hashing 或 openai，默认 hashing（离线确定性）
 * @param baseUrl        阿里云百炼 OpenAI 兼容 embeddings 端点
 * @param apiKey         对应 EMBEDDING_API_KEY
 * @param model          默认 qwen3.7-text-embedding-flash
 * @param dimension      向量维度，默认 1024（需与 pgvector 列一致）
 * @param timeoutSeconds 读超时秒数
 */
@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int dimension,
        int timeoutSeconds
) {

    /**
     * @return 有效向量维度，缺省或非法时回退 1024
     */
    public int resolvedDimension() {
        return dimension <= 0 ? 1024 : dimension;
    }

    /**
     * @return 有效超时秒数，缺省或非法时回退 60
     */
    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 60 : timeoutSeconds;
    }
}
