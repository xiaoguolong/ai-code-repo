package com.aicode.demo.infrastructure.memory;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMemoryAdapterTest {

    @Test
    void shouldReturnEmptyList_whenSessionMissing() {
        InMemoryMemoryAdapter adapter = new InMemoryMemoryAdapter();

        assertThat(adapter.load("missing")).isEmpty();
    }

    @Test
    void shouldIsolateSessions() {
        InMemoryMemoryAdapter adapter = new InMemoryMemoryAdapter();
        adapter.replace("s1", List.of(new ChatMessage(MessageRole.USER, "a")));
        adapter.replace("s2", List.of(new ChatMessage(MessageRole.USER, "b")));

        assertThat(adapter.load("s1").get(0).content()).isEqualTo("a");
        assertThat(adapter.load("s2").get(0).content()).isEqualTo("b");
    }
}
