package com.aicode.demo.infrastructure.persistence;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.infrastructure.persistence.mapper.ChatMessageMapper;
import com.aicode.demo.infrastructure.persistence.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MyBatisConversationAdapterTest {

    @Autowired
    private MyBatisConversationAdapter adapter;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Test
    void shouldCreateSession_whenEnsuringNewSession() {
        adapter.ensureSession("s1", "deepseek-chat");

        assertThat(sessionMapper.listByMap(false, Map.of("sessionId", "s1"))).isNotEmpty();
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
