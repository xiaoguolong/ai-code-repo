package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.model.DocumentChunkView;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentChunkQueryPort;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查看文档分块（Week 32 增补）：校验知识库归属与文档归属后返回切片正文。
 */
@Service
public class ListDocumentChunksUseCase {

    private final KnowledgeBasePort knowledgeBasePort;
    private final DocumentPort documentPort;
    private final DocumentChunkQueryPort chunkQueryPort;

    public ListDocumentChunksUseCase(
            KnowledgeBasePort knowledgeBasePort,
            DocumentPort documentPort,
            DocumentChunkQueryPort chunkQueryPort
    ) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.documentPort = documentPort;
        this.chunkQueryPort = chunkQueryPort;
    }

    /**
     * @param kbId       知识库
     * @param documentId 文档
     * @param userId     当前用户
     * @return 切片（按序号升序）
     * @throws NotFoundException 知识库/文档不存在
     * @throws ForbiddenException 知识库不属于当前用户
     */
    public List<DocumentChunkView> list(long kbId, String documentId, long userId) {
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(kbId)
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (knowledgeBase.userId() != userId) {
            throw new ForbiddenException("access denied");
        }
        boolean belongs = documentPort.listByKnowledgeBase(kbId).stream()
                .map(DocumentMetadata::documentId)
                .anyMatch(documentId::equals);
        if (!belongs) {
            throw new NotFoundException("document not found");
        }
        return chunkQueryPort.listByDocument(documentId);
    }
}
