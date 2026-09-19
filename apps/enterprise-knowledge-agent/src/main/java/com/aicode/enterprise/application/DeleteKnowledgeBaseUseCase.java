package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.core.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

/**
 * 删除知识库用例：校验归属后级联删除文档与切片。
 */
@Service
public class DeleteKnowledgeBaseUseCase {

    private final GetKnowledgeBaseUseCase getKnowledgeBaseUseCase;
    private final KnowledgeBasePort knowledgeBasePort;
    private final DocumentPort documentPort;
    private final VectorStorePort vectorStorePort;

    public DeleteKnowledgeBaseUseCase(
            GetKnowledgeBaseUseCase getKnowledgeBaseUseCase,
            KnowledgeBasePort knowledgeBasePort,
            DocumentPort documentPort,
            VectorStorePort vectorStorePort
    ) {
        this.getKnowledgeBaseUseCase = getKnowledgeBaseUseCase;
        this.knowledgeBasePort = knowledgeBasePort;
        this.documentPort = documentPort;
        this.vectorStorePort = vectorStorePort;
    }

    /**
     * 删除知识库及其全部文档与切片。
     */
    public void delete(Long userId, Long knowledgeBaseId) {
        getKnowledgeBaseUseCase.get(userId, knowledgeBaseId);
        for (DocumentMetadata document : documentPort.listByKnowledgeBase(knowledgeBaseId)) {
            vectorStorePort.deleteByDocumentId(document.documentId());
            documentPort.deleteByDocumentId(document.documentId());
        }
        knowledgeBasePort.deleteById(knowledgeBaseId);
    }
}
