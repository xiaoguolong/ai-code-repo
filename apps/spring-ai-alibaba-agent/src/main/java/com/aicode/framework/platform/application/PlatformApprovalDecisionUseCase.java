package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.followup.application.FollowUpTaskUseCase;
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
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 人工审批决策用例（Week 23；Week 24 增加随访任务与事务）。
 *
 * <p>Week 24（TD-02）：整个决策包在一个事务里，顺序是
 * <b>原子抢决策权 → 写记忆 → 生成随访任务 → 改执行记录终态</b>。
 * 最后一步失败会回滚审批状态，不会留下 {@code APPROVED + PENDING_APPROVAL} 的组合；
 * 记忆/任务失败只降级（见 {@link PlatformApprovalSideEffects}），不触发回滚。
 * 历史脏数据由 {@code ApprovalReconciliationScheduler} 对账修复。</p>
 *
 * <p>事务只在 JDBC 模式生效（单测/内存模式没有事务管理器，行为退化为「尽力而为」，已知边界）。</p>
 */
@Service
public class PlatformApprovalDecisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovalDecisionUseCase.class);

    /** 审批意见长度上限。 */
    public static final int MAX_COMMENT_LENGTH = 500;

    private final PlatformApprovalPort approvalPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformExecutionRecordWriter executionRecordWriter;
    private final PlatformApprovalSideEffects sideEffects;

    /**
     * Week 23 兼容构造器（不含随访任务，供既有单测直接 new）。
     *
     * @param approvalPort         审批任务端口
     * @param executionRecordPort  执行记录
     * @param memoryUseCase        长期记忆
     * @param userPort             用户
     * @param permissionChecker    权限
     * @param auditLogPort         审计
     * @param objectMapper         JSON
     * @param clock                UTC 时钟
     * @param executionRecordWriter 执行记录终态写入
     */
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
        this(approvalPort, executionRecordPort, userPort, permissionChecker, auditLogPort,
                objectMapper, clock, executionRecordWriter,
                new PlatformApprovalSideEffects(memoryUseCase, null));
    }

    /**
     * Spring 注入构造器（Week 24 增加随访任务副作用）。
     *
     * <p>多个构造器必须显式 {@code @Autowired}（第 19/20 周真机踩过启动失败）。</p>
     *
     * @param approvalPort         审批任务端口
     * @param executionRecordPort  执行记录
     * @param userPort             用户
     * @param permissionChecker    权限
     * @param auditLogPort         审计
     * @param objectMapper         JSON
     * @param clock                UTC 时钟
     * @param executionRecordWriter 执行记录终态写入
     * @param memoryUseCase        长期记忆
     * @param followUpTaskUseCase  随访任务（Week 24）
     */
    @Autowired
    public PlatformApprovalDecisionUseCase(
            PlatformApprovalPort approvalPort,
            ExecutionRecordPort executionRecordPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            ObjectMapper objectMapper,
            PlatformExecutionRecordWriter executionRecordWriter,
            PlatformMemoryUseCase memoryUseCase,
            FollowUpTaskUseCase followUpTaskUseCase,
            Clock clock
    ) {
        this(approvalPort, executionRecordPort, userPort, permissionChecker, auditLogPort,
                objectMapper, clock, executionRecordWriter,
                new PlatformApprovalSideEffects(memoryUseCase, followUpTaskUseCase));
    }

    private PlatformApprovalDecisionUseCase(
            PlatformApprovalPort approvalPort,
            ExecutionRecordPort executionRecordPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformExecutionRecordWriter executionRecordWriter,
            PlatformApprovalSideEffects sideEffects
    ) {
        this.approvalPort = approvalPort;
        this.executionRecordPort = executionRecordPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.objectMapper = objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.executionRecordWriter = executionRecordWriter;
        this.sideEffects = sideEffects == null
                ? new PlatformApprovalSideEffects(null, null) : sideEffects;
    }

    /**
     * 批准或拒绝审批任务。
     *
     * @param callerUserId 决策人
     * @param approvalId   审批任务 ID
     * @param approved     true=批准（写记忆 + 生成随访任务），false=拒绝（都不做）
     * @param comment      审批意见，可为空，最长 500
     * @return 决策结果（审批终态 + 执行终态 + memoryId + 随访任务）
     * @throws PlatformNotFoundException 审批/执行/用户不存在
     * @throws PlatformConflictException 已被决策（并发或重复）
     */
    @Transactional
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
        List<String> followUpTaskIds = List.of();
        if (approved) {
            PlatformApprovalSideEffects.Result effects = sideEffects.apply(
                    approval, owner, reportOf(output), decidedAt);
            memoryId = effects.memoryId();
            followUpTaskIds = effects.followUpTaskIds();
            output.put("approvalStatus", ApprovalStatus.APPROVED.name());
            output.put("memoryId", memoryId);
            output.put("followUpTaskIds", followUpTaskIds);
            executionRecordWriter.saveTerminal(execution, ExecutionStatus.COMPLETED, output, decidedAt);
            audit(callerUserId, AuditAction.APPROVAL_DECIDE, "approval:" + approval.approvalId(),
                    AuditResult.SUCCESS, "approvalId=" + approval.approvalId()
                            + " executionId=" + approval.executionId()
                            + " approved=true memoryId=" + memoryId
                            + " followUpTasks=" + followUpTaskIds.size());
            log.info("[platform] approval approved approvalId={} executionId={} decidedBy={} memoryId={} followUpTasks={}",
                    approval.approvalId(), approval.executionId(), callerUserId, memoryId, followUpTaskIds.size());
        } else {
            output.put("approvalStatus", ApprovalStatus.REJECTED.name());
            output.put("followUpTaskIds", List.of());
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
                memoryId,
                followUpTaskIds);
    }

    private String reportOf(Map<String, Object> output) {
        Object report = output.get("report");
        return report == null ? "" : String.valueOf(report);
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
