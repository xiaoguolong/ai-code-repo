package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志查询用例（Week 16）。
 *
 * <p>审计数据含全平台操作轨迹，因此读取遵循最小权限：admin 可见全部，
 * 非 admin 只能看到自己的记录。</p>
 */
@Service
public class PlatformAuditUseCase {

    private final AuditLogPort auditLogPort;
    private final PlatformPermissionChecker permissionChecker;

    public PlatformAuditUseCase(AuditLogPort auditLogPort, PlatformPermissionChecker permissionChecker) {
        this.auditLogPort = auditLogPort;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 列出调用者可见的审计记录，最新在前。
     *
     * @param userId 调用者用户 ID
     * @return 审计记录列表，无数据返回空列表（admin 为全部，其余仅本人）
     */
    public List<AuditLogEntry> listAuditLogs(long userId) {
        if (permissionChecker.isAdmin(userId)) {
            return auditLogPort.listAll();
        }
        return auditLogPort.listByUserId(userId);
    }
}
