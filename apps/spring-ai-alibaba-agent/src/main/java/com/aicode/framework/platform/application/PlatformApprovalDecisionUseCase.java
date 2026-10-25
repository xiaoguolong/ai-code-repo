package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 人工审批决策用例（Week 23）：批准写随访记忆，拒绝不写；决策不调用模型、不重跑工具。
 *
 * <p>与 {@link PlatformApprovalUseCase}（创建/查询待办）拆开：本类只处理状态迁移与副作用，
 * 便于把「谁可以审」与「审批后做什么」分别单测，也避免单类超过 200 行。</p>
 *
 * <p>并发：先用 {@link PlatformApprovalPort#markDecided} 原子抢决策权，抢输方得到 409，
 * 保证只有一个人能触发「写记忆 + 改执行状态」。</p>
 */
@Service
public class PlatformApprovalDecisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovalDecisionUseCase.class);

    /** 审批意见长度上限。 */
    public static final int MAX_COMMENT_LENGTH = 500;

    private final PlatformApprovalPort approvalPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformMemoryUseCase memoryUseCase;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformExecutionRecordWriter executionRecordWriter;

    public PlatformApprovalDecisionUseCase(
            PlatformApprovalPort approvalPort,
            ExecutionRecordPort executionRecordPort,
            PlatformMemoryUseCase memoryUseCase,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformExecutionRecordWriter executionRecordWriter
    ) {
        this.approvalPort = approvalPort;
        this.executionRecordPort = executionRecordPort;
        this.memoryUseCase = memoryUseCase;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.executionRecordWriter = executionRecordWriter;
    }

    /**
     * 批准或拒绝审批任务。
     *
     * @param callerUserId 决策人
     * @param approvalId   审批任务 ID
     * @param approved     true=批准（写记忆），false=拒绝（不写记忆）
     * @param comment      审批意见，可为空，最长 500
     * @return 决策结果（审批终态 + 执行终态 + memoryId）
     * @throws PlatformNotFoundException 审批/执行/用户不存在
     * @throws PlatformConflictException 已被决策（并发或重复）
     */
    public ApprovalDecision decide(long callerUserId, String approvalId, boolean approved, String comment) {
        String normalizedComment = normalizeComment(comment);
        PlatformApproval approval = requireApproval(approvalId);
        permissionChecker.requireApprovalAccess(callerUserId, approval.userId());
        if (!approval.pending()) {
            throw new PlatformConflictException("approval is not pending: " + approval.approvalId());
        }
        ExecutionRecord execution = executionRecordPort.findById(approval.executionId())
                .orElseThrow(() -> new PlatformNotFoundException(
                        "execution not found: " + approval.executionId()));
        Map<String, Object> output = PlatformRunOutputJson.parse(objectMapper, execution.outputJson());
        PlatformUser owner = approved ? requireUser(approval.userId()) : null;

        Instant decidedAt = clock.instant();
        ApprovalStatus target = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        boolean marked = approvalPort.markDecided(
                approval.approvalId(), target, callerUserId, normalizedComment, decidedAt);
        if (!marked) {
            throw new PlatformConflictException("approval is not pending: " + approval.approvalId());
        }

        String memoryId = null;
        if (approved) {
            memoryId = writeFollowUpMemory(owner, approval, output);
            output.put("approvalStatus", ApprovalStatus.APPROVED.name());
            output.put("memoryId", memoryId);
            executionRecordWriter.saveTerminal(execution, ExecutionStatus.COMPLETED, output, decidedAt);
            audit(callerUserId, AuditAction.APPROVAL_DECIDE, "approval:" + approval.approvalId(),
                    AuditResult.SUCCESS, "approvalId=" + approval.approvalId()
                            + " executionId=" + approval.executionId()
                            + " approved=true memoryId=" + memoryId);
            log.info("[platform] approval approved approvalId={} executionId={} decidedBy={} memoryId={}",
                    approval.approvalId(), approval.executionId(), callerUserId, memoryId);
        } else {
            output.put("approvalStatus", ApprovalStatus.REJECTED.name());
            executionRecordWriter.saveTerminal(execution, ExecutionStatus.REJECTED, output, decidedAt);
            audit(callerUserId, AuditAction.APPROVAL_DECIDE, "approval:" + approval.approvalId(),
                    AuditResult.DENIED, "approvalId=" + approval.approvalId()
                            + " executionId=" + approval.executionId() + " approved=false");
            log.info("[platform] approval rejected approvalId={} executionId={} decidedBy={}",
                    approval.approvalId(), approval.executionId(), callerUserId);
        }

        PlatformApproval decided = approvalPort.findById(approval.approvalId())
                .orElseThrow(() -> new PlatformNotFoundException("approval not found: " + approvalId));
        return new ApprovalDecision(
                decided,
                approved ? ExecutionStatus.COMPLETED : ExecutionStatus.REJECTED,
                memoryId);
    }

    private String writeFollowUpMemory(PlatformUser owner, PlatformApproval approval, Map<String, Object> output) {
        String report = output.get("report") == null ? "" : String.valueOf(output.get("report"));
        try {
            PlatformMemoryRecord saved = memoryUseCase.save(
                    PlatformMemoryScope.of(owner), approval.patientId(), approval.task(), report);
            return saved.memoryId();
        } catch (RuntimeException ex) {
            // 记忆不是主链路单点：审批决定已经生效，记忆写失败只告警（与第 21 周口径一致）。
            log.warn("[platform] approval memory save failed approvalId={} patientId={} error={}",
                    approval.approvalId(), approval.patientId(), ex.getMessage());
            return null;
        }
    }

    private PlatformApproval requireApproval(String approvalId) {
        String normalized = requireText(approvalId, "approvalId");
        return approvalPort.findById(normalized)
                .orElseThrow(() -> new PlatformNotFoundException("approval not found: " + normalized));
    }

    private PlatformUser requireUser(long userId) {
        return userPort.findById(userId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + userId));
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return "";
        }
        String normalized = comment.trim();
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidChatRequestException(
                    "comment must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return normalized;
    }

    private void audit(
            long userId,
            AuditAction action,
            String resource,
            AuditResult result,
            String detail
    ) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, action, resource, result, TraceIds.current(), detail));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action={} userId={} error={}",
                    action, userId, ex.getMessage());
        }
    }
}
