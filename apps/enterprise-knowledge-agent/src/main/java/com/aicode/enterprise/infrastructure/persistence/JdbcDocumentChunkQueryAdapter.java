package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.DocumentChunkView;
import com.aicode.enterprise.domain.port.DocumentChunkQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 切片正文查询适配器（Week 32 增补，表 {@code document_chunk}，仅 Postgres/pgvector）。
 *
 * <p>只查正文与序号，不查向量，避免把 1024 维数组带到接口层。</p>
 */
@Component
public class JdbcDocumentChunkQueryAdapter implements DocumentChunkQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentChunkQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunkView> listByDocument(String documentId) {
        return jdbcTemplate.query(
                "SELECT chunk_index, content FROM document_chunk WHERE document_id = ? ORDER BY chunk_index ASC",
                (rs, rowNum) -> new DocumentChunkView(rs.getInt("chunk_index"), rs.getString("content")),
                documentId);
    }
}
