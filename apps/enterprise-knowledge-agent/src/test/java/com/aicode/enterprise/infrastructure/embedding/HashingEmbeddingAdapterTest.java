package com.aicode.enterprise.infrastructure.embedding;

import com.aicode.enterprise.infrastructure.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线确定性向量化单元测试。
 */
class HashingEmbeddingAdapterTest {

    private final HashingEmbeddingAdapter adapter =
            new HashingEmbeddingAdapter(new EmbeddingProperties("hashing", null, null, null, 1024, 60));

    @Test
    void producesFixedDimension() {
        assertThat(adapter.embed("hello world")).hasSize(1024);
    }

    @Test
    void isDeterministic() {
        assertThat(adapter.embed("hello world")).isEqualTo(adapter.embed("hello world"));
    }

    @Test
    void blankTextReturnsZeroVector() {
        float[] vector = adapter.embed("   ");
        for (float value : vector) {
            assertThat(value).isEqualTo(0.0f);
        }
    }
}
