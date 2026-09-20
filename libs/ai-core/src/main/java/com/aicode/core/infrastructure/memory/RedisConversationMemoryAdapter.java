package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.port.MemoryPort;
import com.aicode.core.infrastructure.config.MemoryProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的短期记忆，支持多实例共享会话。键带 {@code agent:memory:} 前缀与 TTL，
 * 读取脏数据时回退空列表，不打断主流程。仅当 {@code memory.short-term-provider=redis}
 * 且类路径存在 Redis 客户端时装配。
 */
@Component
@ConditionalOnProperty(name = "memory.short-term-provider", havingValue = "redis")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class RedisConversationMemoryAdapter implements MemoryPort {

    /** Redis key 前缀。 */
    private static final String KEY_PREFIX = "agent:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisConversationMemoryAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MemoryProperties memoryProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = memoryProperties.resolvedRedisTtl();
    }

    @Override
    public List<ChatMessage> load(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ChatMessage> messages = objectMapper.readValue(json, new TypeReference<>() {
            });
            return messages == null ? List.of() : List.copyOf(messages);
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    public void append(String sessionId, List<ChatMessage> messages) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<ChatMessage> merged = new ArrayList<>(load(sessionId));
        merged.addAll(messages);
        try {
            redisTemplate.opsForValue().set(key(sessionId), objectMapper.writeValueAsString(merged), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to save agent memory to redis", ex);
        }
    }

    @Override
    public void clear(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
