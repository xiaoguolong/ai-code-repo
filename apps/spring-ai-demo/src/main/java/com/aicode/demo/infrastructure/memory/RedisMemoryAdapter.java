package com.aicode.demo.infrastructure.memory;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.port.MemoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的短期记忆。键过期时间 24 小时，窗口大小由用例层控制。
 */
@Component
@ConditionalOnProperty(name = "chat.memory-provider", havingValue = "redis")
public class RedisMemoryAdapter implements MemoryPort {

    /** Redis key 前缀。 */
    private static final String KEY_PREFIX = "chat:memory:";

    /** 过期时间。 */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMemoryAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChatMessage> load(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    public void replace(String sessionId, List<ChatMessage> messages) {
        Objects.requireNonNull(sessionId, "sessionId");
        try {
            String json = objectMapper.writeValueAsString(messages);
            redisTemplate.opsForValue().set(key(sessionId), json, TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to save chat memory to redis", ex);
        }
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
