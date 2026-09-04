package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 创建知识库用例：校验名称 → 落库。
 */
@Service
public class CreateKnowledgeBaseUseCase {

    private final KnowledgeBasePort knowledgeBasePort;
    private final Clock clock;

    public CreateKnowledgeBaseUseCase(KnowledgeBasePort knowledgeBasePort, Clock clock) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.clock = clock;
    }

    /**
     * @throws InvalidChatRequestException 名称为空
     */
    public KnowledgeBase create(Long userId, String name, String description) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new InvalidChatRequestException("name must not be blank");
        }
        return knowledgeBasePort.create(new KnowledgeBase(null, userId, trimmedName, description, clock.instant()));
    }
}
