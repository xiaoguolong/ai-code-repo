package com.aicode.demo.application;

import com.aicode.demo.domain.model.TokenUsage;

/**
 * 聊天用例出参。供 Controller 组装 HTTP 响应，不暴露厂商类型。
 */
public record ChatOutcome(
        String sessionId,
        String messageId,
        String content,
        TokenUsage usage,
        String model
) {
}
