package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.AuditLogEntry;

import java.util.List;

/**
 * 审计日志端口（企业审计规范，Week 16）。
 *
 * <p>实现约定：{@link #record} 失败不得抛出后吞掉主流程语义——调用方负责 try/catch 兜底；
 * {@link #listByUserId} 与 {@link #listAll} 必须返回空列表而非 {@code null}，
 * 并按 {@code createdAt} 倒序（最新在前）。</p>
 */
public interface AuditLogPort {

    /**
     * 写入一条审计记录。
     *
     * @param entry 审计条目，{@code auditId} 由实现回填，入参可为 0
     */
    void record(AuditLogEntry entry);

    /**
     * 查询指定用户的审计记录，最新在前。
     *
     * @param userId 用户 ID
     * @return 审计记录列表，无数据返回空列表
     */
    List<AuditLogEntry> listByUserId(long userId);

    /**
     * 查询全部审计记录，最新在前。
     *
     * @return 审计记录列表，无数据返回空列表
     */
    List<AuditLogEntry> listAll();
}
