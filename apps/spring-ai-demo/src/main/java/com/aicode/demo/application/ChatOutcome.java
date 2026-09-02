package com.aicode.demo.application;

import com.aicode.demo.domain.model.TokenUsage;

import java.util.Map;

/**
 * 聊天用例出参。供 Controller 组装 HTTP 响应，不暴露厂商类型。
 *
 * @param payload JSON 输出时解析出的对象；TEXT 时为 null
 */
public record ChatOutcome(
        String sessionId,
        String messageId,
        String content,
        TokenUsage usage,
        String model,
        Map<String, Object> payload
) {

    /**
     * 文本输出构造器。
     */
    public ChatOutcome(String sessionId, String messageId, String content, TokenUsage usage, String model) {
        this(sessionId, messageId, content, usage, model, null);
    }
}
