package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.ChatMessage;

import java.util.List;

/**
 * 出站端口：会话与消息持久化。Redis miss 时可从这里回填历史。
 */
public interface ConversationPort {

    /**
     * 确保会话存在；不存在则创建。
     *
     * @param sessionId 会话标识
     * @param model     默认模型名
     */
    void ensureSession(String sessionId, String model);

    /**
     * 追加一条消息。只应追加 user/assistant，system 由用例层决定是否落库。
     *
     * @param sessionId 会话标识
     * @param message   消息
     */
    void append(String sessionId, ChatMessage message);

    /**
     * 按时间顺序列出会话消息。
     *
     * @param sessionId 会话标识
     * @return 消息列表，不存在时为空
     */
    List<ChatMessage> list(String sessionId);
}
