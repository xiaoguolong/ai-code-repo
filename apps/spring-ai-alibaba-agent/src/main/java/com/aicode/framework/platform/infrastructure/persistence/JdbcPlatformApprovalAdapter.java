package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 审批任务落库适配器（Week 23，表 {@code platform_approval}，迁移 V6；Week 27 加提醒记账）。
 *
 * <p>决策用单条 {@code UPDATE ... WHERE status='PENDING'} 保证并发下只有一个赢家，
 * 避免两个审核人同时批准时写出两条随访记忆。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformApprovalAdapter implements PlatformApprovalPort {

    private static final String SELECT_COLUMNS = """
            SELECT approval_id, execution_id, tenant_key, user_id, agent_key, patient_id,
                   risk_level, task, status, comment, decided_by, created_at, decided_at
            FROM platform_approval
            """;

    /** 带别名的同名列（Week 27：与提醒表 JOIN 时避免歧义）。 */
    private static final String SELECT_ALIASED_COLUMNS = """
            SELECT a.approval_id, a.execution_id, a.tenant_key, a.user_id, a.agent_key, a.patient_id,
                   a.risk_level, a.task, a.status, a.comment, a.decided_by, a.created_at, a.decided_at
            FROM platform_approval a
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformApproval> rowMapper = new PlatformApprovalRowMapper();

    public JdbcPlatformApprovalAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void save(PlatformApproval approval) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_approval SET execution_id = ?, tenant_key = ?, user_id = ?, agent_key = ?,
                    patient_id = ?, risk_level = ?, task = ?, status = ?, comment = ?, decided_by = ?,
                    created_at = ?, decided_at = ?
                WHERE approval_id = ?
                """,
                approval.executionId(), approval.tenantKey(), approval.userId(), approval.agentKey(),
                approval.patientId(), approval.riskLevel(), approval.task(), approval.status().name(),
                approval.comment(), approval.decidedBy(),
                PlatformJdbcSupport.toTimestamp(approval.createdAt()),
                PlatformJdbcSupport.toTimestamp(approval.decidedAt()),
                approval.approvalId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_approval (
                        approval_id, execution_id, tenant_key, user_id, agent_key, patient_id,
                        risk_level, task, status, comment, decided_by, created_at, decided_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    approval.approvalId(), approval.executionId(), approval.tenantKey(),
                    approval.userId(), approval.agentKey(), approval.patientId(), approval.riskLevel(),
                    approval.task(), approval.status().name(), approval.comment(), approval.decidedBy(),
                    PlatformJdbcSupport.toTimestamp(approval.createdAt()),
                    PlatformJdbcSupport.toTimestamp(approval.decidedAt()));
        }
    }

    @Override
    public Optional<PlatformApproval> findById(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE approval_id = ?", rowMapper, approvalId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<PlatformApproval> listByUser(long userId, ApprovalStatus status, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE user_id = ? AND status = ?"
                        + " ORDER BY created_at DESC, approval_id DESC LIMIT ?",
                rowMapper, userId, status.name(), limit);
    }

    @Override
    public List<PlatformApproval> listAll(ApprovalStatus status, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = ? ORDER BY created_at DESC, approval_id DESC LIMIT ?",
                rowMapper, status.name(), limit);
    }

    @Override
    public long countAll(ApprovalStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_approval WHERE status = ?", Long.class, status.name());
        return count == null ? 0L : count;
    }

    @Override
    public long countByUser(long userId, ApprovalStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_approval WHERE user_id = ? AND status = ?",
                Long.class, userId, status.name());
        return count == null ? 0L : count;
    }

    @Override
    public List<PlatformApproval> listPendingCreatedBefore(long userId, Instant createdBefore, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'PENDING' AND user_id = ? AND created_at <= ?"
                        + " ORDER BY created_at ASC, approval_id ASC LIMIT ?",
                rowMapper, userId, PlatformJdbcSupport.toTimestamp(createdBefore), limit);
    }

    @Override
    public List<PlatformApproval> listAllPendingCreatedBefore(Instant createdBefore, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'PENDING' AND created_at <= ?"
                        + " ORDER BY created_at ASC, approval_id ASC LIMIT ?",
                rowMapper, PlatformJdbcSupport.toTimestamp(createdBefore), limit);
    }

    @Override
    public List<PlatformApproval> listRemindable(
            Instant createdBefore,
            Instant remindedBefore,
            int limit
    ) {
        return jdbcTemplate.query(
                SELECT_ALIASED_COLUMNS
                        + " LEFT JOIN platform_approval_reminder r ON r.approval_id = a.approval_id"
                        + " WHERE a.status = 'PENDING' AND a.created_at <= ?"
                        + " AND (r.last_reminded_at IS NULL OR r.last_reminded_at <= ?)"
                        + " ORDER BY a.created_at ASC, a.approval_id ASC LIMIT ?",
                rowMapper,
                PlatformJdbcSupport.toTimestamp(createdBefore),
                PlatformJdbcSupport.toTimestamp(remindedBefore),
                limit);
    }

    @Override
    public void markReminded(String approvalId, Instant remindedAt) {
        if (approvalId == null || approvalId.isBlank()) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE platform_approval_reminder
                SET reminder_count = reminder_count + 1, last_reminded_at = ?
                WHERE approval_id = ?
                """, PlatformJdbcSupport.toTimestamp(remindedAt), approvalId.trim());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_approval_reminder (approval_id, reminder_count, last_reminded_at)
                    VALUES (?, 1, ?)
                    """, approvalId.trim(), PlatformJdbcSupport.toTimestamp(remindedAt));
        }
    }

    @Override
    public boolean markDecided(
            String approvalId,
            ApprovalStatus status,
            long decidedBy,
            String comment,
            Instant decidedAt
    ) {
        if (approvalId == null || approvalId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE platform_approval
                SET status = ?, decided_by = ?, comment = ?, decided_at = ?
                WHERE approval_id = ? AND status = 'PENDING'
                """, status.name(), decidedBy, comment == null ? "" : comment,
                PlatformJdbcSupport.toTimestamp(decidedAt), approvalId.trim()) > 0;
    }
}