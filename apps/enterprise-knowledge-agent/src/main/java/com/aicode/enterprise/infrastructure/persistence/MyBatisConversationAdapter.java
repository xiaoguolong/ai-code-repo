package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.ChatMessage;
import com.aicode.enterprise.domain.model.MessageRole;
import com.aicode.enterprise.domain.port.ConversationPort;
import com.aicode.enterprise.infrastructure.persistence.entity.ChatMessageEntity;
import com.aicode.enterprise.infrastructure.persistence.entity.ChatSessionEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.ChatMessageMapper;
import com.aicode.enterprise.infrastructure.persistence.mapper.ChatSessionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Fluent-MyBatis 的会话持久化适配器。
 */
@Component
public class MyBatisConversationAdapter implements ConversationPort {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final Clock clock;

    public MyBatisConversationAdapter(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            Clock clock
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> sessionOwner(String sessionId) {
        return sessionMapper.listByMap(false, Map.of("sessionId", sessionId)).stream()
                .findFirst()
                .map(ChatSessionEntity::getUserId);
    }

    @Override
    @Transactional
    public void ensureSession(String sessionId, Long userId, Long knowledgeBaseId, String title) {
        List<ChatSessionEntity> found = sessionMapper.listByMap(false, Map.of("sessionId", sessionId));
        if (found.isEmpty()) {
            sessionMapper.insert(new ChatSessionEntity(sessionId, userId, knowledgeBaseId, title, clock.instant()));
        }
    }

    @Override
    @Transactional
    public void append(String sessionId, ChatMessage message) {
        if (message.role() == MessageRole.SYSTEM) {
            return;
        }
        messageMapper.insert(new ChatMessageEntity(
                sessionId,
                message.role().name(),
                message.content(),
                clock.instant()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(String sessionId) {
        return messageMapper.listByMap(false, Map.of("sessionId", sessionId)).stream()
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                .map(entity -> new ChatMessage(parseRole(entity.getRole()), entity.getContent()))
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
