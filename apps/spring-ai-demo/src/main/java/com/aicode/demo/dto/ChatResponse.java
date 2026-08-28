package com.aicode.demo.dto;

import com.aicode.demo.application.ChatOutcome;

/**
 * 聊天接口成功体。
 */
public record ChatResponse(String sessionId, String messageId, String content, UsageDto usage) {

    /**
     * 从用例出参转换，隔离应用层类型。
     */
    public static ChatResponse from(ChatOutcome outcome) {
        return new ChatResponse(
                outcome.sessionId(),
                outcome.messageId(),
                outcome.content(),
                new UsageDto(
                        outcome.usage().promptTokens(),
                        outcome.usage().completionTokens(),
                        outcome.usage().totalTokens()
                )
        );
    }
}
