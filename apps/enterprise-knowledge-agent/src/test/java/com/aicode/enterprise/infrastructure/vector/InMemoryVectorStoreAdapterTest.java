package com.aicode.enterprise.infrastructure.vector;

import com.aicode.enterprise.domain.model.DocumentChunk;
import com.aicode.enterprise.domain.model.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存向量库单元测试：按知识库范围过滤 + 删除。
 */
class InMemoryVectorStoreAdapterTest {

    private final InMemoryVectorStoreAdapter adapter = new InMemoryVectorStoreAdapter();

    @Test
    void filtersByKnowledgeBase() {
        adapter.put(new DocumentChunk("doc-1", 1L, 0, "内容A"), new float[]{1f, 0f});
        adapter.put(new DocumentChunk("doc-2", 2L, 0, "内容B"), new float[]{0f, 1f});

        List<VectorSearchHit> hits = adapter.search(new float[]{1f, 0f}, 10, 1L);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    void returnsEmptyForUnknownKnowledgeBase() {
        adapter.put(new DocumentChunk("doc-1", 1L, 0, "内容A"), new float[]{1f, 0f});

        assertThat(adapter.search(new float[]{1f, 0f}, 10, 99L)).isEmpty();
    }

    @Test
    void deletesByDocumentId() {
        adapter.put(new DocumentChunk("doc-1", 1L, 0, "内容A"), new float[]{1f, 0f});
        adapter.deleteByDocumentId("doc-1");

        assertThat(adapter.search(new float[]{1f, 0f}, 10, 1L)).isEmpty();
    }
}
