package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformSessionPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 平台用户管理用例（Week 28，TD-06）：列表 / 停用 / 恢复。
 *
 * <p><b>只停用不硬删</b>：执行记录、审批、随访、记忆、审计都引用用户，物理删除会破坏历史；
 * 停用只写 {@code disabled_at} 并踢出平台会话。</p>
 *
 * <p>安全约束：仅 admin；禁止停用自己（避免唯一管理员自锁）；重复停用/恢复幂等。</p>
 */
@Service
public class PlatformUserAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserAdminUseCase.class);

    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final PlatformSessionPort sessionPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public PlatformUserAdminUseCase(
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            PlatformSessionPort sessionPort,
            AuditLogPort auditLogPort,
            Clock clock
    ) {
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.sessionPort = sessionPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 列出全部用户（仅 admin）。
     *
     * @param callerUserId 调用者
     * @return 用户视图列表（按 userId 升序）
     * @throws PlatformAccessDeniedException 非 admin
     */
    public List<PlatformUserView> list(long callerUserId) {
        requireAdmin(callerUserId);
        return userPort.listAll().stream().map(PlatformUserView::from).toList();
    }

    /**
     * 停用用户（仅 admin）：写停用时间 + 踢出平台会话 + 审计。
     *
     * @param callerUserId 管理员
     * @param userId       目标用户
     * @return 停用后的用户视图
     * @throws PlatformAccessDeniedException 非 admin
     * @throws PlatformNotFoundException     目标不存在
     * @throws InvalidChatRequestException   停用自己
     */
    public PlatformUserView disable(long callerUserId, long userId) {
        requireAdmin(callerUserId);
        PlatformUser target = requireUser(userId);
        if (target.id() == callerUserId) {
            throw new InvalidChatRequestException("cannot disable yourself");
        }
        if (target.disabled()) {
            return PlatformUserView.from(target);
        }
        Instant now = clock.instant();
        if (!userPort.updateDisabledAt(userId, now)) {
            throw new PlatformNotFoundException("user not found: " + userId);
        }
        revokeSessions(userId);
        audit(callerUserId, AuditAction.USER_DISABLE, target);
        log.info("[platform] user disabled userId={} username={} by={}", userId, target.username(), callerUserId);
        return new PlatformUserView(
                target.id(), target.username(), target.roleKey(), target.tenantKey(), true, now);
    }

    /**
     * 恢复用户（仅 admin）：清空停用时间 + 审计；不自动恢复旧会话。
     *
     * @param callerUserId 管理员
     * @param userId       目标用户
     * @return 恢复后的用户视图
     * @throws PlatformAccessDeniedException 非 admin
     * @throws PlatformNotFoundException     目标不存在
     */
    public PlatformUserView enable(long callerUserId, long userId) {
        requireAdmin(callerUserId);
        PlatformUser target = requireUser(userId);
        if (!target.disabled()) {
            return PlatformUserView.from(target);
        }
        if (!userPort.updateDisabledAt(userId, null)) {
            throw new PlatformNotFoundException("user not found: " + userId);
        }
        audit(callerUserId, AuditAction.USER_ENABLE, target);
        log.info("[platform] user enabled userId={} username={} by={}", userId, target.username(), callerUserId);
        return new PlatformUserView(
                target.id(), target.username(), target.roleKey(), target.tenantKey(), false, null);
    }

    private PlatformUser requireUser(long userId) {
        return userPort.findById(userId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + userId));
    }

    private void requireAdmin(long callerUserId) {
        if (!permissionChecker.isAdmin(callerUserId)) {
            throw new PlatformAccessDeniedException("admin role is required for user management");
        }
    }

    private void revokeSessions(long userId) {
        try {
            sessionPort.revokeAll(userId);
        } catch (RuntimeException ex) {
            // 停用已落库；/auth/me 的 disabled 检查会兜底，不能因为踢会话失败回滚停用
            log.warn("[platform] revoke sessions failed userId={} error={}", userId, ex.getMessage());
        }
    }

    private void audit(long callerUserId, AuditAction action, PlatformUser target) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    callerUserId, action, "user:" + target.id(), AuditResult.SUCCESS,
                    TraceIds.current(), "userId=" + target.id() + " username=" + target.username()));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action={} userId={} error={}",
                    action, target.id(), ex.getMessage());
        }
    }
}
