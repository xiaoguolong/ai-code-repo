package com.aicode.demo.infrastructure.memory;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.port.MemoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内短期记忆。默认启用；chat.memory-provider=redis 时让位给 Redis 适配器。
 */
@Component
@ConditionalOnProperty(name = "chat.memory-provider", havingValue = "memory", matchIfMissing = true)
public class InMemoryMemoryAdapter implements MemoryPort {

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> load(String sessionId) {
        List<ChatMessage> found = store.get(sessionId);
        if (found == null) {
            return List.of();
        }
        return List.copyOf(found);
    }

    @Override
    public void replace(String sessionId, List<ChatMessage> messages) {
        Objects.requireNonNull(sessionId, "sessionId");
        store.put(sessionId, List.copyOf(messages));
    }
}
