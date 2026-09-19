package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.enterprise.domain.port.ConversationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 会话历史用例单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ListChatHistoryUseCaseTest {

    @Mock
    private ConversationPort conversationPort;

    private ListChatHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListChatHistoryUseCase(conversationPort);
    }

    @Test
    void returnsMessagesForOwnSession() {
        when(conversationPort.sessionOwner("sess-1")).thenReturn(Optional.of(1L));
        when(conversationPort.listMessages("sess-1"))
                .thenReturn(List.of(new ChatMessage(MessageRole.USER, "你好")));

        assertThat(useCase.list(1L, "sess-1")).hasSize(1);
    }

    @Test
    void forbidsOthersSession() {
        when(conversationPort.sessionOwner("sess-1")).thenReturn(Optional.of(2L));

        assertThatThrownBy(() -> useCase.list(1L, "sess-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void throwsNotFoundWhenSessionMissing() {
        when(conversationPort.sessionOwner("sess-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.list(1L, "sess-x")).isInstanceOf(NotFoundException.class);
    }
}
