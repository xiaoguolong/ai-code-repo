package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.port.DocumentPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出某知识库文档用例：校验归属后返回文档元数据。
 */
@Service
public class ListDocumentsUseCase {

    private final GetKnowledgeBaseUseCase getKnowledgeBaseUseCase;
    private final DocumentPort documentPort;

    public ListDocumentsUseCase(GetKnowledgeBaseUseCase getKnowledgeBaseUseCase, DocumentPort documentPort) {
        this.getKnowledgeBaseUseCase = getKnowledgeBaseUseCase;
        this.documentPort = documentPort;
    }

    /**
     * @return 该知识库下的文档元数据列表
     */
    public List<DocumentMetadata> list(Long userId, Long knowledgeBaseId) {
        getKnowledgeBaseUseCase.get(userId, knowledgeBaseId);
        return documentPort.listByKnowledgeBase(knowledgeBaseId);
    }
}
