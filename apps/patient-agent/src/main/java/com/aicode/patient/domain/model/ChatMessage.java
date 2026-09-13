package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * 单条对话消息。role 与 content 成对出现，供编排层组装上下文。
 */
public record ChatMessage(MessageRole role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
