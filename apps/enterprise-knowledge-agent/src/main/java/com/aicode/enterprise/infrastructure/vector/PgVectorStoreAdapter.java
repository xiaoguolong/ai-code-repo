package com.aicode.enterprise.infrastructure.vector;

import com.aicode.enterprise.domain.model.DocumentChunk;
import com.aicode.enterprise.domain.model.VectorSearchHit;
import com.aicode.enterprise.domain.port.VectorStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 pgvector 的向量库。仅在 PostgreSQL 且 rag.vector-store=pgvector 时启用。
 * 依赖迁移 V6 创建 document_chunk 表与 vector 列；单测用内存实现替代。
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "pgvector")
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final String INSERT_SQL =
            "INSERT INTO document_chunk (document_id, knowledge_base_id, chunk_index, content, embedding) "
                    + "VALUES (?, ?, ?, ?, ?::vector)";
    private static final String SEARCH_SQL =
            "SELECT document_id, knowledge_base_id, chunk_index, content, 1 - (embedding <=> ?::vector) AS score "
                    + "FROM document_chunk WHERE knowledge_base_id = ? "
                    + "ORDER BY embedding <=> ?::vector LIMIT ?";
    private static final String DELETE_SQL =
            "DELETE FROM document_chunk WHERE document_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void put(DocumentChunk chunk, float[] vector) {
        jdbcTemplate.update(INSERT_SQL,
                chunk.documentId(), chunk.knowledgeBaseId(), chunk.chunkIndex(), chunk.content(), formatVector(vector));
    }

    @Override
    public List<VectorSearchHit> search(float[] query, int topK, long knowledgeBaseId) {
        String vector = formatVector(query);
        return jdbcTemplate.query(SEARCH_SQL,
                (rs, rowNum) -> new VectorSearchHit(
                        rs.getString("document_id"),
                        rs.getLong("knowledge_base_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ),
                vector, knowledgeBaseId, vector, topK);
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update(DELETE_SQL, documentId);
    }

    /**
     * 把向量转成 pgvector 的文本数组字面量，如 {@code [1.0,-2.0,0.0]}。
     */
    static String formatVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
