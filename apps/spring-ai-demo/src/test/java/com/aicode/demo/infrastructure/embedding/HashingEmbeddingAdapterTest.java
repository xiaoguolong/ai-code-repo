package com.aicode.demo.infrastructure.embedding;

import com.aicode.demo.infrastructure.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线确定性向量化适配器：同文同向量、维度可配、结果归一化。
 */
class HashingEmbeddingAdapterTest {

    @Test
    void shouldProduceVectorOfConfiguredDimension() {
        HashingEmbeddingAdapter adapter = adapter(8);

        assertThat(adapter.embed("hello world")).hasSize(8);
    }

    @Test
    void shouldBeDeterministic_andIdenticalForSameText() {
        HashingEmbeddingAdapter adapter = adapter(16);

        assertThat(adapter.embed("hello")).containsExactly(adapter.embed("hello"));
    }

    @Test
    void shouldDifferForDifferentText() {
        HashingEmbeddingAdapter adapter = adapter(32);

        assertThat(adapter.embed("apple")).isNotEqualTo(adapter.embed("banana"));
    }

    @Test
    void shouldNormalizeToUnitLength() {
        HashingEmbeddingAdapter adapter = adapter(16);

        float[] vector = adapter.embed("some longer text with several tokens");
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    private HashingEmbeddingAdapter adapter(int dimension) {
        return new HashingEmbeddingAdapter(new EmbeddingProperties(
                "hashing", null, null, null, dimension, 60
        ));
    }
}
