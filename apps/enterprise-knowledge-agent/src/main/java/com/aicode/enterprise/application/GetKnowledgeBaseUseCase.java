package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

/**
 * 查询单个知识库用例：校验归属，越权 403。
 */
@Service
public class GetKnowledgeBaseUseCase {

    private final KnowledgeBasePort knowledgeBasePort;

    public GetKnowledgeBaseUseCase(KnowledgeBasePort knowledgeBasePort) {
        this.knowledgeBasePort = knowledgeBasePort;
    }

    /**
     * @throws NotFoundException 知识库不存在
     * @throws ForbiddenException 不属于当前用户
     */
    public KnowledgeBase get(Long userId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(knowledgeBaseId)
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (!knowledgeBase.userId().equals(userId)) {
            throw new ForbiddenException("access denied");
        }
        return knowledgeBase;
    }
}
