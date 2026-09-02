package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.TestClockConfig;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.infrastructure.persistence.repository.ChatMessageRepository;
import com.aicode.demo.infrastructure.persistence.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaConversationAdapter.class, TestClockConfig.class})
@ActiveProfiles("test")
class JpaConversationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Autowired
    private JpaConversationAdapter adapter;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    void shouldCreateSession_whenEnsuringNewSession() {
        adapter.ensureSession("s1", "deepseek-chat");

        assertThat(sessionRepository.findBySessionId("s1")).isPresent();
    }

    @Test
    void shouldAppendMessagesAndListThem() {
        adapter.ensureSession("s2", "deepseek-chat");

        adapter.append("s2", new ChatMessage(MessageRole.USER, "hi"));
        adapter.append("s2", new ChatMessage(MessageRole.ASSISTANT, "hello"));
        adapter.append("s2", new ChatMessage(MessageRole.SYSTEM, "ignored"));

        List<ChatMessage> messages = adapter.list("s2");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).content()).isEqualTo("hi");
        assertThat(messages.get(1).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void shouldReturnEmptyList_forUnknownSession() {
        List<ChatMessage> messages = adapter.list("unknown");

        assertThat(messages).isEmpty();
    }
}
