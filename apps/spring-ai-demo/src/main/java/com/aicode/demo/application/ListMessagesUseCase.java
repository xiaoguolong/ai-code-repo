package com.aicode.demo.application;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.port.ConversationPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出会话历史消息。
 */
@Service
public class ListMessagesUseCase {

    private final ConversationPort conversationPort;

    public ListMessagesUseCase(ConversationPort conversationPort) {
        this.conversationPort = conversationPort;
    }

    /**
     * 按时间顺序列出会话消息。
     *
     * @param sessionId 会话标识
     * @return 消息列表
     */
    public List<ChatMessage> list(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return conversationPort.list(sessionId.trim());
    }
}
