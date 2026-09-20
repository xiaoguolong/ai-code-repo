package com.aicode.core.infrastructure.memory;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内短期记忆适配器测试。
 */
class InMemoryConversationMemoryAdapterTest {

    private final InMemoryConversationMemoryAdapter adapter = new InMemoryConversationMemoryAdapter();

    @Test
    void loadsEmptyWhenSessionUnknown() {
        assertThat(adapter.load("unknown")).isEmpty();
    }

    @Test
    void appendsAndLoadsMessagesInOrder() {
        adapter.append("s1", List.of(new ChatMessage(MessageRole.USER, "u1")));
        adapter.append("s1", List.of(
                new ChatMessage(MessageRole.ASSISTANT, "a1"),
                new ChatMessage(MessageRole.USER, "u2")
        ));

        List<ChatMessage> loaded = adapter.load("s1");

        assertThat(loaded).extracting(ChatMessage::content).containsExactly("u1", "a1", "u2");
    }

    @Test
    void isolatesSessions() {
        adapter.append("s1", List.of(new ChatMessage(MessageRole.USER, "a")));
        adapter.append("s2", List.of(new ChatMessage(MessageRole.USER, "b")));

        assertThat(adapter.load("s1")).extracting(ChatMessage::content).containsExactly("a");
        assertThat(adapter.load("s2")).extracting(ChatMessage::content).containsExactly("b");
    }

    @Test
    void clearsSession() {
        adapter.append("s1", List.of(new ChatMessage(MessageRole.USER, "a")));

        adapter.clear("s1");

        assertThat(adapter.load("s1")).isEmpty();
    }
}
