package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.ChatMessage;

/**
 * 会话消息项。
 */
public record MessageDto(String role, String content) {

    /**
     * 从领域模型转换。
     */
    public static MessageDto from(ChatMessage message) {
        return new MessageDto(message.role().name(), message.content());
    }
}
