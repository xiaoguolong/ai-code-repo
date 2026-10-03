package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.AuditLogEntry;

import java.time.Instant;

/**
 * 审计日志响应体（Week 16）。
 *
 * <p>只暴露标识与结果，不含任何凭据或请求正文。</p>
 *
 * @param userId    操作者用户 ID，未登录为 0
 * @param action    审计动作（{@code LOGIN} / {@code LOGIN_FAILED} / {@code AGENT_RUN} / {@code EXECUTION_READ}）
 * @param resource  操作对象标识
 * @param result    结果（{@code SUCCESS} / {@code FAILURE} / {@code DENIED}）
 * @param traceId   链路追踪 ID
 * @param detail    脱敏说明
 * @param createdAt 发生时间（UTC）
 */
public record AuditLogResponse(
        long userId,
        String action,
        String resource,
        String result,
        String traceId,
        String detail,
        Instant createdAt
) {

    /**
     * 领域条目 → 响应体。
     *
     * @param entry 审计条目
     * @return 响应体
     */
    public static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.userId(),
                entry.action().name(),
                entry.resource(),
                entry.result().name(),
                entry.traceId(),
                entry.detail(),
                entry.createdAt());
    }
}
