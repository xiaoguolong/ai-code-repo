package com.aicode.demo.domain.model;

import java.time.Instant;

/**
 * 单次聊天审计记录。可记输入/输出预览，禁止包含 API Key。
 */
public record ChatAuditRecord(
        Instant occurredAt,
        String sessionId,
        String model,
        String inputPreview,
        String outputPreview,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        AuditStatus status,
        String errorCode
) {
}
