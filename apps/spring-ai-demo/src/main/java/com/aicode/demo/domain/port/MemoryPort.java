package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.ChatMessage;

import java.util.List;

/**
 * 出站端口：会话短期记忆。只存 user/assistant，不含 system。
 * 第2周 Redis 实现；单测用内存实现。
 */
public interface MemoryPort {

    /**
     * 读取会话历史。不存在时返回空列表，永不返回 null。
     *
     * @param sessionId 会话标识
     * @return 按时间顺序的历史消息
     */
    List<ChatMessage> load(String sessionId);

    /**
     * 用截断后的窗口整体替换会话历史。
     *
     * @param sessionId 会话标识
     * @param messages  不含 system 的消息列表
     */
    void replace(String sessionId, List<ChatMessage> messages);
}
