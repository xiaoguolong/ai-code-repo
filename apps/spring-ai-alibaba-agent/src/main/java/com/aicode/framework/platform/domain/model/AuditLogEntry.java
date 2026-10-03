package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 审计日志条目（企业审计规范，Week 16）。
 *
 * <p>约束：只记录「谁 / 何时 / 对什么 / 做了什么 / 结果」，禁止写入密码、token、API Key
 * 与完整 Prompt 正文；{@code detail} 必须由调用方传入已脱敏文本，长度上限 512。</p>
 *
 * @param auditId   数据库自增主键；写入前传 0，由适配器回填
 * @param userId    操作者用户 ID；未登录或登录失败时为 0
 * @param action    审计动作
 * @param resource  操作对象标识，格式 {@code <资源类型>:<标识>}，如 {@code agent:patient-risk}
 * @param result    操作结果
 * @param traceId   链路追踪 ID，来自 MDC（无请求上下文时为空串）
 * @param detail    脱敏后的补充说明，可为空串
 * @param createdAt 发生时间（UTC）
 */
public record AuditLogEntry(
        long auditId,
        long userId,
        AuditAction action,
        String resource,
        AuditResult result,
        String traceId,
        String detail,
        Instant createdAt
) {

    /** {@code detail} 字段长度上限，超出由适配器截断。 */
    public static final int MAX_DETAIL_LENGTH = 512;

    /**
     * 调用方友好的构造入口：主键置 0、时间取当前 UTC。
     *
     * @param userId   操作者用户 ID，未知传 0
     * @param action   审计动作，不可为 null
     * @param resource 操作对象标识，不可为 null
     * @param result   操作结果，不可为 null
     * @param traceId  链路追踪 ID，可为 null（等价空串）
     * @param detail   脱敏说明，可为 null（等价空串）
     */
    public static AuditLogEntry of(
            long userId,
            AuditAction action,
            String resource,
            AuditResult result,
            String traceId,
            String detail
    ) {
        return new AuditLogEntry(
                0L,
                userId,
                action,
                resource == null ? "" : resource,
                result,
                traceId == null ? "" : traceId,
                detail == null ? "" : detail,
                Instant.now());
    }
}
