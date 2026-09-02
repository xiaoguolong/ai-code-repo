package com.aicode.demo.domain;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.model.PromptTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装发给模型的消息：system + 滑动窗口历史 + 本轮 user。
 * 历史中的 system 一律丢弃，防止用户内容被写进系统提示。
 */
public final class ChatContextAssembler {

    private ChatContextAssembler() {
    }

    /**
     * 组装一次模型调用的完整消息列表。
     *
     * @param prompt              系统提示
     * @param history             短期记忆（user/assistant）
     * @param userMessage         本轮用户输入
     * @param maxHistoryMessages  历史条数上限（不含 system）
     * @return 不可变消息列表，首条必为 system
     */
    public static List<ChatMessage> assemble(
            PromptTemplate prompt,
            List<ChatMessage> history,
            String userMessage,
            int maxHistoryMessages
    ) {
        List<ChatMessage> truncated = truncate(history, maxHistoryMessages);
        List<ChatMessage> messages = new ArrayList<>(truncated.size() + 2);
        messages.add(new ChatMessage(MessageRole.SYSTEM, prompt.content()));
        messages.addAll(truncated);
        messages.add(new ChatMessage(MessageRole.USER, userMessage));
        return List.copyOf(messages);
    }

    /**
     * 追加本轮 user/assistant 并按窗口截断，结果写回记忆。
     *
     * @return 不含 system 的新窗口
     */
    public static List<ChatMessage> appendAndTruncate(
            List<ChatMessage> history,
            ChatMessage user,
            ChatMessage assistant,
            int maxHistoryMessages
    ) {
        List<ChatMessage> next = new ArrayList<>(history.size() + 2);
        next.addAll(withoutSystem(history));
        next.add(user);
        next.add(assistant);
        return truncate(next, maxHistoryMessages);
    }

    static List<ChatMessage> truncate(List<ChatMessage> history, int maxHistoryMessages) {
        List<ChatMessage> cleaned = withoutSystem(history);
        if (cleaned.size() <= maxHistoryMessages) {
            return List.copyOf(cleaned);
        }
        int from = cleaned.size() - maxHistoryMessages;
        return List.copyOf(cleaned.subList(from, cleaned.size()));
    }

    private static List<ChatMessage> withoutSystem(List<ChatMessage> history) {
        return history.stream()
                .filter(message -> message.role() != MessageRole.SYSTEM)
                .toList();
    }
}
