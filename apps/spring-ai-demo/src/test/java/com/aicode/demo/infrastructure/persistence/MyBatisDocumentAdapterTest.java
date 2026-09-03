package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.DocumentMetadata;
import com.aicode.demo.infrastructure.persistence.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基于 Fluent-MyBatis 的文档元数据适配器（H2 仓储测试）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MyBatisDocumentAdapterTest {

    @Autowired
    private MyBatisDocumentAdapter adapter;

    @Autowired
    private DocumentMapper documentMapper;

    @Test
    void shouldCreateAndListDocuments() {
        adapter.create(new DocumentMetadata("doc-1", "知识库文档", Instant.parse("2026-09-03T00:00:00Z")));
        adapter.create(new DocumentMetadata("doc-2", "流程手册", Instant.parse("2026-09-03T00:00:00Z")));

        List<DocumentMetadata> documents = adapter.list();

        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(DocumentMetadata::documentId)
                .containsExactlyInAnyOrder("doc-1", "doc-2");
    }

    @Test
    void shouldReturnEmpty_whenNoDocuments() {
        assertThat(adapter.list()).isEmpty();
        assertThat(documentMapper.listByMapAndDefault(Map.of())).isEmpty();
    }
}
