package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

/**
 * 更新知识库用例：校验归属后更新名称与描述。
 */
@Service
public class UpdateKnowledgeBaseUseCase {

    private final GetKnowledgeBaseUseCase getKnowledgeBaseUseCase;
    private final KnowledgeBasePort knowledgeBasePort;

    public UpdateKnowledgeBaseUseCase(
            GetKnowledgeBaseUseCase getKnowledgeBaseUseCase,
            KnowledgeBasePort knowledgeBasePort
    ) {
        this.getKnowledgeBaseUseCase = getKnowledgeBaseUseCase;
        this.knowledgeBasePort = knowledgeBasePort;
    }

    /**
     * @return 更新后的知识库
     */
    public KnowledgeBase update(UpdateKnowledgeBaseCommand command) {
        String trimmedName = command.name() == null ? "" : command.name().trim();
        if (trimmedName.isEmpty()) {
            throw new InvalidChatRequestException("name must not be blank");
        }
        KnowledgeBase existing = getKnowledgeBaseUseCase.get(command.userId(), command.knowledgeBaseId());
        KnowledgeBase updated = new KnowledgeBase(
                existing.id(),
                existing.userId(),
                trimmedName,
                command.description(),
                existing.createdAt()
        );
        knowledgeBasePort.update(updated);
        return updated;
    }
}
