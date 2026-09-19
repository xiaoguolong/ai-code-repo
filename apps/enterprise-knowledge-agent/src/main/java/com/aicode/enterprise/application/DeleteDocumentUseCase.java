package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.core.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

/**
 * 删除文档用例：校验知识库归属后删除切片与元数据。
 */
@Service
public class DeleteDocumentUseCase {

    private final GetKnowledgeBaseUseCase getKnowledgeBaseUseCase;
    private final DocumentPort documentPort;
    private final VectorStorePort vectorStorePort;

    public DeleteDocumentUseCase(
            GetKnowledgeBaseUseCase getKnowledgeBaseUseCase,
            DocumentPort documentPort,
            VectorStorePort vectorStorePort
    ) {
        this.getKnowledgeBaseUseCase = getKnowledgeBaseUseCase;
        this.documentPort = documentPort;
        this.vectorStorePort = vectorStorePort;
    }

    /**
     * 删除文档及其全部切片。
     */
    public void delete(Long userId, Long knowledgeBaseId, String documentId) {
        getKnowledgeBaseUseCase.get(userId, knowledgeBaseId);
        vectorStorePort.deleteByDocumentId(documentId);
        documentPort.deleteByDocumentId(documentId);
    }
}
