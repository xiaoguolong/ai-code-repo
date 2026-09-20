package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.infrastructure.config.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 短期记忆适配器测试。Mock StringRedisTemplate，不连真实 Redis。
 */
@ExtendWith(MockitoExtension.class)
class RedisConversationMemoryAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisConversationMemoryAdapter adapter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new RedisConversationMemoryAdapter(
                redisTemplate, objectMapper, new MemoryProperties("redis", "memory", 20, 3, 24));
    }

    private RedisConversationMemoryAdapter adapterWithoutValueOps() {
        return new RedisConversationMemoryAdapter(
                redisTemplate, objectMapper, new MemoryProperties("redis", "memory", 20, 3, 24));
    }

    @Test
    void loadsEmptyWhenKeyMissing() {
        when(valueOperations.get("agent:memory:s1")).thenReturn(null);

        assertThat(adapter().load("s1")).isEmpty();
    }

    @Test
    void parsesStoredJson() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(
                new ChatMessage(MessageRole.USER, "u1"),
                new ChatMessage(MessageRole.ASSISTANT, "a1")
        ));
        when(valueOperations.get("agent:memory:s1")).thenReturn(json);

        assertThat(adapter().load("s1")).extracting(ChatMessage::content).containsExactly("u1", "a1");
    }

    @Test
    void returnsEmptyOnCorruptJson() {
        when(valueOperations.get("agent:memory:s1")).thenReturn("not-json");

        assertThat(adapter().load("s1")).isEmpty();
    }

    @Test
    void appendMergesExistingMessagesAndSetsTtl() throws Exception {
        String existing = objectMapper.writeValueAsString(List.of(new ChatMessage(MessageRole.USER, "u1")));
        when(valueOperations.get("agent:memory:s1")).thenReturn(existing);

        adapter().append("s1", List.of(new ChatMessage(MessageRole.ASSISTANT, "a1")));

        verify(valueOperations).set(eq("agent:memory:s1"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void clearDeletesKey() {
        adapterWithoutValueOps().clear("s1");

        verify(redisTemplate).delete("agent:memory:s1");
    }
}
