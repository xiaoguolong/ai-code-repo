package com.aicode.core.domain;

import com.aicode.core.domain.model.ChatMessage;

import java.util.List;

/**
 * 短期记忆窗口截断（纯函数）。只保留最近 N 条消息，防止上下文与 Token 无界增长。
 */
public final class MemoryWindow {

    private MemoryWindow() {
    }

    /**
     * 取历史消息的最近 maxMessages 条。
     *
     * @param history     历史消息，可为 null
     * @param maxMessages 窗口上限；非正数返回空列表
     * @return 新的只读列表，永不返回 null
     */
    public static List<ChatMessage> trim(List<ChatMessage> history, int maxMessages) {
        if (history == null || history.isEmpty() || maxMessages <= 0) {
            return List.of();
        }
        if (history.size() <= maxMessages) {
            return List.copyOf(history);
        }
        return List.copyOf(history.subList(history.size() - maxMessages, history.size()));
    }
}
