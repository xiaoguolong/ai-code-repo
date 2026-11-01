package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.model.DocumentChunkView;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentChunkQueryPort;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查看文档分块用例测试（Week 32 增补）。
 */
class ListDocumentChunksUseCaseTest {

    private final KnowledgeBase kb = new KnowledgeBase(1L, 42L, "库", "", Instant.parse("2026-11-01T00:00:00Z"));
    private final DocumentMetadata doc = new DocumentMetadata(
            "doc-1", 1L, "guide.pdf", 2, Instant.parse("2026-11-01T00:00:00Z"));

    private final KnowledgeBasePort kbPort = new KnowledgeBasePort() {
        @Override
        public KnowledgeBase create(KnowledgeBase knowledgeBase) {
            return knowledgeBase;
        }

        @Override
        public Optional<KnowledgeBase> findById(long id) {
            return id == 1L ? Optional.of(kb) : Optional.empty();
        }

        @Override
        public List<KnowledgeBase> listByUser(long userId) {
            return List.of(kb);
        }

        @Override
        public void update(KnowledgeBase knowledgeBase) {
        }

        @Override
        public void deleteById(long id) {
        }
    };

    private final DocumentPort documentPort = new DocumentPort() {
        @Override
        public void create(DocumentMetadata metadata) {
        }

        @Override
        public List<DocumentMetadata> listByKnowledgeBase(long knowledgeBaseId) {
            return List.of(doc);
        }

        @Override
        public void deleteByDocumentId(String documentId) {
        }
    };

    private final DocumentChunkQueryPort chunkPort =
            documentId -> List.of(new DocumentChunkView(0, "第一段"), new DocumentChunkView(1, "第二段"));

    private final ListDocumentChunksUseCase useCase = new ListDocumentChunksUseCase(kbPort, documentPort, chunkPort);

    @Test
    void returnsChunksForOwnedDocument() {
        assertThat(useCase.list(1L, "doc-1", 42L))
                .extracting(DocumentChunkView::content)
                .containsExactly("第一段", "第二段");
    }

    @Test
    void rejectsOtherUsersKnowledgeBase() {
        assertThatThrownBy(() -> useCase.list(1L, "doc-1", 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsDocumentNotInKnowledgeBase() {
        assertThatThrownBy(() -> useCase.list(1L, "doc-other", 42L))
                .isInstanceOf(NotFoundException.class);
    }
}
