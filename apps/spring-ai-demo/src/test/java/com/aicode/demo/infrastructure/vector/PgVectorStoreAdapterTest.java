package com.aicode.demo.infrastructure.vector;

import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.VectorSearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * pgvector 适配器：仅校验 SQL 参数与向量文本格式，不连真实 PG。
 */
@ExtendWith(MockitoExtension.class)
class PgVectorStoreAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PgVectorStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PgVectorStoreAdapter(jdbcTemplate);
    }

    @Test
    void shouldFormatVectorAsPgArray() {
        assertThat(PgVectorStoreAdapter.formatVector(new float[]{1.5f, -2f, 0f}))
                .isEqualTo("[1.5,-2.0,0.0]");
    }

    @Test
    void shouldInsertChunkWithVectorCast() {
        adapter.put(new DocumentChunk("doc-1", 0, "content"), new float[]{1f, 2f, 3f});

        verify(jdbcTemplate).update(
                eq("INSERT INTO document_chunk (document_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?::vector)"),
                eq("doc-1"), eq(0), eq("content"), eq("[1.0,2.0,3.0]")
        );
    }

    @Test
    void shouldSearchAndMapHits() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("[1.0,0.0]"), eq("[1.0,0.0]"), eq(3)))
                .thenReturn(List.of(new VectorSearchHit("doc-1", 1, "hit content", 0.9)));

        List<VectorSearchHit> hits = adapter.search(new float[]{1f, 0f}, 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).content()).isEqualTo("hit content");
        assertThat(hits.get(0).score()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-6));
    }
}
