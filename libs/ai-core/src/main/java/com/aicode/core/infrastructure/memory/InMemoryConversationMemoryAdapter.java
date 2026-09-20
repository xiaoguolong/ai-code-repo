package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.port.MemoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内短期记忆。默认启用；{@code memory.short-term-provider=redis} 时让位给 Redis 适配器。
 * 适合单实例；多实例共享会话需切 Redis。
 */
@Component
@ConditionalOnProperty(name = "memory.short-term-provider", havingValue = "memory", matchIfMissing = true)
public class InMemoryConversationMemoryAdapter implements MemoryPort {

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> load(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        List<ChatMessage> found = store.get(sessionId);
        return found == null ? List.of() : List.copyOf(found);
    }

    @Override
    public void append(String sessionId, List<ChatMessage> messages) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (messages == null || messages.isEmpty()) {
            return;
        }
        store.compute(sessionId, (key, existing) -> {
            List<ChatMessage> merged = new ArrayList<>(existing == null ? List.of() : existing);
            merged.addAll(messages);
            return List.copyOf(merged);
        });
    }

    @Override
    public void clear(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        store.remove(sessionId);
    }
}
