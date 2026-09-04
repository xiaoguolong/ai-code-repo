package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出当前用户知识库用例。
 */
@Service
public class ListKnowledgeBasesUseCase {

    private final KnowledgeBasePort knowledgeBasePort;

    public ListKnowledgeBasesUseCase(KnowledgeBasePort knowledgeBasePort) {
        this.knowledgeBasePort = knowledgeBasePort;
    }

    /**
     * @return 当前用户全部知识库，无则空列表
     */
    public List<KnowledgeBase> listByUser(Long userId) {
        return knowledgeBasePort.listByUser(userId);
    }
}
