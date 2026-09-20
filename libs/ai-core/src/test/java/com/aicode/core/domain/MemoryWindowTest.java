package com.aicode.core.domain;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 短期记忆窗口截断纯函数测试。
 */
class MemoryWindowTest {

    @Test
    void keepsMostRecentMessagesWhenExceedingLimit() {
        List<ChatMessage> history = List.of(
                new ChatMessage(MessageRole.USER, "u1"),
                new ChatMessage(MessageRole.ASSISTANT, "a1"),
                new ChatMessage(MessageRole.USER, "u2"),
                new ChatMessage(MessageRole.ASSISTANT, "a2")
        );

        List<ChatMessage> trimmed = MemoryWindow.trim(history, 2);

        assertThat(trimmed).hasSize(2);
        assertThat(trimmed.get(0).content()).isEqualTo("u2");
        assertThat(trimmed.get(1).content()).isEqualTo("a2");
    }

    @Test
    void returnsCopyWhenWithinLimit() {
        List<ChatMessage> history = List.of(new ChatMessage(MessageRole.USER, "u1"));

        assertThat(MemoryWindow.trim(history, 10)).containsExactlyElementsOf(history);
    }

    @Test
    void returnsEmptyForNullOrEmptyHistory() {
        assertThat(MemoryWindow.trim(null, 5)).isEmpty();
        assertThat(MemoryWindow.trim(List.of(), 5)).isEmpty();
    }

    @Test
    void returnsEmptyForNonPositiveLimit() {
        List<ChatMessage> history = List.of(new ChatMessage(MessageRole.USER, "u1"));

        assertThat(MemoryWindow.trim(history, 0)).isEmpty();
        assertThat(MemoryWindow.trim(history, -1)).isEmpty();
    }
}
