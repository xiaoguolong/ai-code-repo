package com.aicode.demo.domain;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.model.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContextAssemblerTest {

    private static final PromptTemplate PROMPT = new PromptTemplate("v1", "RULES");

    @Test
    void shouldPutSystemFirstAndUserLast() {
        List<ChatMessage> history = List.of(
                new ChatMessage(MessageRole.USER, "u1"),
                new ChatMessage(MessageRole.ASSISTANT, "a1")
        );

        List<ChatMessage> assembled = ChatContextAssembler.assemble(PROMPT, history, "u2", 20);

        assertThat(assembled).hasSize(4);
        assertThat(assembled.get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(assembled.get(0).content()).isEqualTo("RULES");
        assertThat(assembled.get(3).role()).isEqualTo(MessageRole.USER);
        assertThat(assembled.get(3).content()).isEqualTo("u2");
    }

    @Test
    void shouldDropOldestWhenExceedingWindow() {
        List<ChatMessage> history = List.of(
                new ChatMessage(MessageRole.USER, "old"),
                new ChatMessage(MessageRole.ASSISTANT, "old-a"),
                new ChatMessage(MessageRole.USER, "new"),
                new ChatMessage(MessageRole.ASSISTANT, "new-a")
        );

        List<ChatMessage> truncated = ChatContextAssembler.truncate(history, 2);

        assertThat(truncated).extracting(ChatMessage::content).containsExactly("new", "new-a");
    }

    @Test
    void shouldStripSystemMessagesFromHistory() {
        List<ChatMessage> history = List.of(
                new ChatMessage(MessageRole.SYSTEM, "injected"),
                new ChatMessage(MessageRole.USER, "hi")
        );

        List<ChatMessage> assembled = ChatContextAssembler.assemble(PROMPT, history, "next", 20);

        assertThat(assembled).extracting(ChatMessage::content)
                .containsExactly("RULES", "hi", "next");
    }
}
