package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.VectorSearchHit;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.domain.port.VectorStorePort;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内部知识库用例（Week 25）：SSO 映射、归属校验、切片检索与文档名回填。
 */
class InternalKnowledgeUseCaseTest {

    private static final long PLATFORM_USER = 2L;
    private static final long ENTERPRISE_USER = 7L;
    private static final long KB_ID = 11L;

    private KnowledgeBasePort knowledgeBasePort;
    private DocumentPort documentPort;
    private SsoUserLinkPort ssoUserLinkPort;
    private EmbeddingModelPort embeddingModelPort;
    private VectorStorePort vectorStorePort;
    private InternalKnowledgeUseCase useCase;

    @BeforeEach
    void setUp() {
        knowledgeBasePort = mock(KnowledgeBasePort.class);
        documentPort = mock(DocumentPort.class);
        ssoUserLinkPort = mock(SsoUserLinkPort.class);
        embeddingModelPort = mock(EmbeddingModelPort.class);
        vectorStorePort = mock(VectorStorePort.class);
        useCase = new InternalKnowledgeUseCase(
                knowledgeBasePort, documentPort, ssoUserLinkPort, embeddingModelPort,
                vectorStorePort, new RagRuntimeConfig(4, 500, 50));
    }

    @Test
    void listsBasesOfTheLinkedEnterpriseUser() {
        when(ssoUserLinkPort.findEnterpriseUserId(PLATFORM_USER)).thenReturn(Optional.of(ENTERPRISE_USER));
        when(knowledgeBasePort.listByUser(ENTERPRISE_USER)).thenReturn(List.of(knowledgeBase()));
        when(documentPort.listByKnowledgeBase(KB_ID)).thenReturn(List.of(
                new DocumentMetadata("D001", KB_ID, "糖尿病指南.pdf", 3, Instant.now())));

        InternalKnowledgeCatalog catalog = useCase.listBases(PLATFORM_USER);

        assertThat(catalog.enterpriseUserId()).isEqualTo(ENTERPRISE_USER);
        assertThat(catalog.bases()).singleElement().satisfies(view -> {
            assertThat(view.knowledgeBaseId()).isEqualTo(KB_ID);
            assertThat(view.name()).isEqualTo("慢病指南");
            assertThat(view.documentCount()).isEqualTo(1);
        });
    }

    @Test
    void unlinkedPlatformUserIsNotFound() {
        when(ssoUserLinkPort.findEnterpriseUserId(PLATFORM_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.listBases(PLATFORM_USER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchesWithOwnershipCheckAndReturnsDocumentNames() {
        when(knowledgeBasePort.findById(KB_ID)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingModelPort.embed("糖尿病随访要点")).thenReturn(new float[]{1.0f, 0.0f});
        when(vectorStorePort.search(new float[]{1.0f, 0.0f}, 4, KB_ID)).thenReturn(List.of(
                new VectorSearchHit("D001", KB_ID, 2, "HbA1c 每 3 个月复查", 0.91),
                new VectorSearchHit("D002", KB_ID, 0, "血压目标 <140/90", 0.82)));
        when(documentPort.listByKnowledgeBase(KB_ID)).thenReturn(List.of(
                new DocumentMetadata("D001", KB_ID, "糖尿病指南.pdf", 3, Instant.now()),
                new DocumentMetadata("D002", KB_ID, "高血压随访.pdf", 2, Instant.now())));

        InternalKnowledgeResult result = useCase.search(new InternalKnowledgeSearchCommand(
                ENTERPRISE_USER, KB_ID, "糖尿病随访要点", null));

        assertThat(result.knowledgeBase().knowledgeBaseId()).isEqualTo(KB_ID);
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).documentName()).isEqualTo("糖尿病指南.pdf");
        assertThat(result.hits().get(0).score()).isEqualTo(0.91);
        verify(vectorStorePort).search(new float[]{1.0f, 0.0f}, 4, KB_ID);
    }

    @Test
    void topKIsClampedToFifty() {
        when(knowledgeBasePort.findById(KB_ID)).thenReturn(Optional.of(knowledgeBase()));
        when(embeddingModelPort.embed("q")).thenReturn(new float[]{1.0f});
        when(vectorStorePort.search(new float[]{1.0f}, 50, KB_ID)).thenReturn(List.of());

        useCase.search(new InternalKnowledgeSearchCommand(ENTERPRISE_USER, KB_ID, "q", 999));

        verify(vectorStorePort).search(new float[]{1.0f}, 50, KB_ID);
    }

    @Test
    void knowledgeBaseOwnedByAnotherUserIsNotFound() {
        when(knowledgeBasePort.findById(KB_ID)).thenReturn(Optional.of(
                new KnowledgeBase(KB_ID, 999L, "别人的库", "", Instant.now())));

        assertThatThrownBy(() -> useCase.search(
                new InternalKnowledgeSearchCommand(ENTERPRISE_USER, KB_ID, "q", 4)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> useCase.search(
                new InternalKnowledgeSearchCommand(ENTERPRISE_USER, KB_ID, "  ", 4)))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    private KnowledgeBase knowledgeBase() {
        return new KnowledgeBase(KB_ID, ENTERPRISE_USER, "慢病指南", "内部知识库", Instant.now());
    }
}
