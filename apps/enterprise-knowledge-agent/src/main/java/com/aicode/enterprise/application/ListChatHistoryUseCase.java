package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.ChatMessage;
import com.aicode.enterprise.domain.port.ConversationPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出会话消息历史用例：校验会话归属，越权 403。
 */
@Service
public class ListChatHistoryUseCase {

    private final ConversationPort conversationPort;

    public ListChatHistoryUseCase(ConversationPort conversationPort) {
        this.conversationPort = conversationPort;
    }

    /**
     * @throws NotFoundException 会话不存在
     * @throws ForbiddenException 会话不属于当前用户
     */
    public List<ChatMessage> list(Long userId, String sessionId) {
        Long owner = conversationPort.sessionOwner(sessionId)
                .orElseThrow(() -> new NotFoundException("session not found"));
        if (!owner.equals(userId)) {
            throw new ForbiddenException("access denied");
        }
        return conversationPort.listMessages(sessionId);
    }
}
