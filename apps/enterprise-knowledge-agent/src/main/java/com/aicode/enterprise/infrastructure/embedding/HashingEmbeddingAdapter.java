package com.aicode.enterprise.infrastructure.embedding;

import com.aicode.enterprise.domain.port.EmbeddingModelPort;
import com.aicode.enterprise.infrastructure.config.EmbeddingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 离线确定性向量化。把文本按词袋哈希到固定维度并归一化，无需密钥与网络。
 * 仅用于本地跑通链路与单测；语义检索请切 {@code embedding.provider=openai}。
 */
@Component
@ConditionalOnProperty(name = "embedding.provider", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingAdapter implements EmbeddingModelPort {

    private final int dimension;

    public HashingEmbeddingAdapter(EmbeddingProperties properties) {
        this.dimension = properties.resolvedDimension();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            int bucket = Math.floorMod(token.hashCode(), dimension);
            vector[bucket] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        if (sum == 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
