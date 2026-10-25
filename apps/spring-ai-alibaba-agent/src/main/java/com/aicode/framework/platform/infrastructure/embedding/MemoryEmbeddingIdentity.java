package com.aicode.framework.platform.infrastructure.embedding;

import com.aicode.core.infrastructure.config.EmbeddingProperties;
import org.springframework.stereotype.Component;

/**
 * 当前记忆向量来源标识（Week 23）：{@code provider:model:dimension}。
 *
 * <p>用途：写入时记录、重建时比对。换 provider / 模型 / 维度任意一项，
 * 旧向量都不可与新查询向量比较，必须重建。默认 hashing 的 model 为空，
 * 统一记为 {@code default}，避免出现 {@code hashing::1024} 这类漂移格式。</p>
 */
@Component
public class MemoryEmbeddingIdentity {

    private final String currentKey;

    public MemoryEmbeddingIdentity(EmbeddingProperties properties) {
        String provider = (properties.provider() == null || properties.provider().isBlank())
                ? "hashing" : properties.provider().trim();
        String model = (properties.model() == null || properties.model().isBlank())
                ? "default" : properties.model().trim();
        this.currentKey = provider + ":" + model + ":" + properties.resolvedDimension();
    }

    /**
     * @return 当前向量来源标识，如 {@code hashing:default:1024}
     */
    public String currentKey() {
        return currentKey;
    }
}
