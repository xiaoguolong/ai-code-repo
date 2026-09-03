package com.aicode.demo.infrastructure.vector;

import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 内存向量库：按余弦相似度降序返回 top-k。
 */
class InMemoryVectorStoreAdapterTest {

    private final InMemoryVectorStoreAdapter store = new InMemoryVectorStoreAdapter();

    @Test
    void shouldRankByCosineSimilarity() {
        store.put(new DocumentChunk("d1", 0, "exact"), vec(1f, 0f, 0f));
        store.put(new DocumentChunk("d2", 0, "orthogonal"), vec(0f, 1f, 0f));

        List<VectorSearchHit> hits = store.search(vec(1f, 0f, 0f), 10);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).content()).isEqualTo("exact");
        assertThat(hits.get(0).score()).isCloseTo(1.0, within(1e-6));
        assertThat(hits.get(1).content()).isEqualTo("orthogonal");
        assertThat(hits.get(1).score()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void shouldReturnEmpty_whenStoreEmpty() {
        assertThat(store.search(vec(1f, 0f, 0f), 5)).isEmpty();
    }

    @Test
    void shouldRespectTopK() {
        store.put(new DocumentChunk("d1", 0, "a"), vec(1f, 0f, 0f));
        store.put(new DocumentChunk("d2", 0, "b"), vec(0f, 1f, 0f));
        store.put(new DocumentChunk("d3", 0, "c"), vec(0f, 0f, 1f));

        List<VectorSearchHit> hits = store.search(vec(1f, 0f, 0f), 1);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).content()).isEqualTo("a");
    }

    private static float[] vec(float... values) {
        return values;
    }
}
