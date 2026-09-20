package com.aicode.core.domain.port;

import com.aicode.core.domain.model.ChatMessage;

import java.util.List;

/**
 * 出站端口：短期会话记忆。按 sessionId 保存最近的 user/assistant 消息，不含 system 提示。
 * 实现可替换：进程内默认，Redis 用于多实例共享。
 */
public interface MemoryPort {

    /**
     * 读取会话历史。不存在时返回空列表，永不返回 null。
     *
     * @param sessionId 会话标识
     * @return 按时间顺序的历史消息（不含 system）
     */
    List<ChatMessage> load(String sessionId);

    /**
     * 追加消息到会话历史尾部。
     *
     * @param sessionId 会话标识
     * @param messages  追加的消息（不含 system）
     */
    void append(String sessionId, List<ChatMessage> messages);

    /**
     * 清空会话历史。
     *
     * @param sessionId 会话标识
     */
    void clear(String sessionId);
}
