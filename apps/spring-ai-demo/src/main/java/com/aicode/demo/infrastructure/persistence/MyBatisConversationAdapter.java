package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.port.ConversationPort;
import com.aicode.demo.infrastructure.persistence.entity.ChatMessageEntity;
import com.aicode.demo.infrastructure.persistence.entity.ChatSessionEntity;
import com.aicode.demo.infrastructure.persistence.mapper.ChatMessageMapper;
import com.aicode.demo.infrastructure.persistence.mapper.ChatSessionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    @Transactional
    public void ensureSession(String sessionId, String model) {
        List<ChatSessionEntity> found = sessionMapper.listByMap(false, Map.of("sessionId", sessionId));
        if (found.isEmpty()) {
            ChatSessionEntity entity = new ChatSessionEntity(sessionId, model, clock.instant());
            sessionMapper.insert(entity);
        }
    }

    @Override
    @Transactional
    public void append(String sessionId, ChatMessage message) {
        if (message.role() == MessageRole.SYSTEM) {
            return;
        }
        ChatMessageEntity entity = new ChatMessageEntity(
                sessionId,
                message.role().name(),
                message.content(),
                clock.instant()
        );
        messageMapper.insert(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> list(String sessionId) {
        List<ChatMessageEntity> found = messageMapper.listByMap(false, Map.of("sessionId", sessionId));
        return found.stream()
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
