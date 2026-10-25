package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 人工审批待办用例（Week 23）：创建任务、查询列表/详情。
 *
 * <p>状态迁移（批准/拒绝）在 {@link PlatformApprovalDecisionUseCase}，本类只负责「待办」，
 * 两者拆开便于分别单测权限与副作用，也避免单类超过 200 行。</p>
 */
@Service
public class PlatformApprovalUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovalUseCase.class);

    /** 待办列表默认条数。 */
    public static final int DEFAULT_LIST_LIMIT = 20;
    /** 待办列表硬上限。 */
    public static final int MAX_LIST_LIMIT = 100;

    private final PlatformApprovalPort approvalPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public PlatformApprovalUseCase(
            PlatformApprovalPort approvalPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            Clock clock
    ) {
        this.approvalPort = approvalPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 为一次 HIGH 风险执行创建审批任务。
     *
     * @param executionId 执行记录 ID（唯一）
     * @param userId      发起人
     * @param agentKey    Agent 标识
     * @param patientId   患者编号
     * @param riskLevel   风险等级
     * @param task        任务原文
     * @return 已创建的审批任务
     * @throws InvalidChatRequestException 必填字段空白
     * @throws PlatformNotFoundException   发起人不存在
     */
    public PlatformApproval create(
            String executionId,
            long userId,
            String agentKey,
            String patientId,
            String riskLevel,
            String task
    ) {
        String normalizedExecutionId = requireText(executionId, "executionId");
        String normalizedAgentKey = requireText(agentKey, "agentKey");
        String normalizedPatientId = requireText(patientId, "patientId");
        String normalizedRiskLevel = requireText(riskLevel, "riskLevel");
        PlatformUser owner = userPort.findById(userId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + userId));

        PlatformApproval approval = new PlatformApproval(
                UUID.randomUUID().toString(),
                normalizedExecutionId,
                owner.tenantKey(),
                owner.id(),
                normalizedAgentKey,
                normalizedPatientId,
                normalizedRiskLevel,
                task == null ? "" : task,
                ApprovalStatus.PENDING,
                "",
                null,
                clock.instant(),
                null);
        approvalPort.save(approval);
        audit(userId, "approval:" + approval.approvalId(),
                "executionId=" + normalizedExecutionId + " approvalId=" + approval.approvalId());
        log.info("[platform] approval submitted approvalId={} executionId={} userId={} patientId={} riskLevel={}",
                approval.approvalId(), normalizedExecutionId, userId, normalizedPatientId, normalizedRiskLevel);
        return approval;
    }

    /**
     * 查询待办/历史（自己；admin 可看全部）。
     *
     * @param callerUserId 调用者
     * @param status       状态过滤
     * @param limit        条数上限
     * @return 审批任务列表
     */
    public List<PlatformApproval> list(long callerUserId, ApprovalStatus status, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        if (permissionChecker.isAdmin(callerUserId)) {
            return approvalPort.listAll(status, effectiveLimit);
        }
        return approvalPort.listByUser(callerUserId, status, effectiveLimit);
    }

    /**
     * 查询审批详情（本人或 admin）。
     *
     * @param callerUserId 调用者
     * @param approvalId   审批任务 ID
     * @return 审批任务
     * @throws PlatformNotFoundException   不存在
     * @throws com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException 越权
     */
    public PlatformApproval get(long callerUserId, String approvalId) {
        PlatformApproval approval = requireApproval(approvalId);
        permissionChecker.requireApprovalAccess(callerUserId, approval.userId());
        return approval;
    }

    private PlatformApproval requireApproval(String approvalId) {
        String normalized = requireText(approvalId, "approvalId");
        return approvalPort.findById(normalized)
                .orElseThrow(() -> new PlatformNotFoundException("approval not found: " + normalized));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return normalized;
    }

    private void audit(long userId, String resource, String detail) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, AuditAction.APPROVAL_SUBMIT, resource, AuditResult.SUCCESS,
                    TraceIds.current(), detail));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action=APPROVAL_SUBMIT userId={} error={}",
                    userId, ex.getMessage());
        }
    }
}
