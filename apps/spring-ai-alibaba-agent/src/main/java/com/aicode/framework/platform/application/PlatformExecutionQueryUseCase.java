package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 执行记录查询（Week 28，包内协作类）：列表 / 详情 + 越权读审计。
 *
 * <p>与调度拆开：查询不写执行记录、不触发 Agent，只做归属校验与
 * {@code EXECUTION_READ/DENIED} 审计。</p>
 */
final class PlatformExecutionQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionQueryUseCase.class);

    private final ExecutionRecordPort executionRecordPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;

    PlatformExecutionQueryUseCase(
            ExecutionRecordPort executionRecordPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort
    ) {
        this.executionRecordPort = executionRecordPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
    }

    /**
     * 列出当前用户可见的执行记录（admin 全部，其余仅本人）。
     *
     * @param userId 调用者
     * @return 按开始时间倒序的记录
     */
    List<ExecutionRecord> listExecutions(long userId) {
        if (permissionChecker.isAdmin(userId)) {
            return executionRecordPort.listAll();
        }
        return executionRecordPort.listByUserId(userId);
    }

    /**
     * 查询执行记录（校验归属），越权写 {@code EXECUTION_READ/DENIED}。
     *
     * @param userId      调用者
     * @param executionId 执行 ID
     * @return 执行记录
     * @throws PlatformNotFoundException     不存在
     * @throws PlatformAccessDeniedException 越权
     */
    ExecutionRecord getExecution(long userId, String executionId) {
        ExecutionRecord record = executionRecordPort.findById(requireKey(executionId))
                .orElseThrow(() -> new PlatformNotFoundException("execution not found: " + executionId));
        try {
            permissionChecker.requireExecutionAccess(userId, record.userId());
        } catch (PlatformAccessDeniedException ex) {
            auditDeniedRead(executionId, userId, ex.getMessage());
            throw ex;
        }
        return record;
    }

    private void auditDeniedRead(String executionId, long userId, String detail) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId,
                    AuditAction.EXECUTION_READ,
                    "execution:" + executionId,
                    AuditResult.DENIED,
                    TraceIds.current(),
                    auditDetail(executionId, detail)));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action=EXECUTION_READ userId={} error={}",
                    userId, ex.getMessage());
        }
    }

    private String auditDetail(String executionId, String detail) {
        String suffix = (detail == null || detail.isBlank()) ? "" : " error=" + detail;
        return "executionId=" + executionId + suffix;
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException("id must not be blank");
        }
        return key;
    }
}
