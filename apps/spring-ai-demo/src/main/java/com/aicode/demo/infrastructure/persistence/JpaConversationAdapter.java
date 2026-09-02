package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.port.ConversationPort;
import com.aicode.demo.infrastructure.persistence.entity.ChatMessageEntity;
import com.aicode.demo.infrastructure.persistence.entity.ChatSessionEntity;
import com.aicode.demo.infrastructure.persistence.repository.ChatMessageRepository;
import com.aicode.demo.infrastructure.persistence.repository.ChatSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 基于 JPA 的会话持久化适配器。
 */
@Component
public class JpaConversationAdapter implements ConversationPort {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final Clock clock;

    public JpaConversationAdapter(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void ensureSession(String sessionId, String model) {
        sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> sessionRepository.save(
                        new ChatSessionEntity(sessionId, model, clock.instant())
                ));
    }

    @Override
    @Transactional
    public void append(String sessionId, ChatMessage message) {
        if (message.role() == MessageRole.SYSTEM) {
            return;
        }
        Instant now = clock.instant();
        messageRepository.save(new ChatMessageEntity(
                sessionId,
                message.role().name(),
                message.content(),
                now
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> list(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(entity -> new ChatMessage(parseRole(entity.role()), entity.content()))
                .toList();
    }

    private MessageRole parseRole(String role) {
        try {
            return MessageRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MessageRole.USER;
        }
    }
}
