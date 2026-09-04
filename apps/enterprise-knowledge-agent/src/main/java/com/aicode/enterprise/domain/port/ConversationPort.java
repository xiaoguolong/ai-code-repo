package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.ChatMessage;

import java.util.List;
import java.util.Optional;

/**
 * 出站端口：会话与消息持久化。多轮上下文直接从这里读取历史。
 */
public interface ConversationPort {

    /**
     * 查询会话归属用户，用于多租户隔离校验。
     *
     * @param sessionId 会话标识
     * @return 归属用户主键，不存在为空
     */
    Optional<Long> sessionOwner(String sessionId);

    /**
     * 确保会话存在；不存在则创建（含归属用户与知识库）。
     *
     * @param sessionId       会话标识
     * @param userId          归属用户
     * @param knowledgeBaseId 归属知识库
     * @param title           会话标题，可为空
     */
    void ensureSession(String sessionId, Long userId, Long knowledgeBaseId, String title);

    /**
     * 追加一条消息。只应追加 user/assistant，system 由用例层决定是否落库。
     */
    void append(String sessionId, ChatMessage message);

    /**
     * 按时间顺序列出会话消息。
     */
    List<ChatMessage> listMessages(String sessionId);
}
