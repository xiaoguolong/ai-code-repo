package com.aicode.demo.dto;

import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;

/**
 * 单条聊天消息响应。
 */
public record ChatMessageDto(String role, String content) {

    /**
     * 从领域消息转换。
     */
    public static ChatMessageDto from(ChatMessage message) {
        return new ChatMessageDto(message.role().apiValue(), message.content());
    }
}
