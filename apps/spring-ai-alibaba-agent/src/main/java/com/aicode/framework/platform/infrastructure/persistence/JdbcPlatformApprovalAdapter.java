package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 审批任务落库适配器（Week 23，表 {@code platform_approval}，迁移 V6）。
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

    /**
     * {@code platform_approval} 行 → 领域模型。
     */
    private static final class PlatformApprovalRowMapper implements RowMapper<PlatformApproval> {

        @Override
        public PlatformApproval mapRow(ResultSet rs, int rowNum) throws SQLException {
            long decidedByValue = rs.getLong("decided_by");
            Long decidedBy = rs.wasNull() ? null : decidedByValue;
            return new PlatformApproval(
                    rs.getString("approval_id"),
                    rs.getString("execution_id"),
                    rs.getString("tenant_key"),
                    rs.getLong("user_id"),
                    rs.getString("agent_key"),
                    rs.getString("patient_id"),
                    rs.getString("risk_level"),
                    rs.getString("task"),
                    ApprovalStatus.valueOf(rs.getString("status")),
                    rs.getString("comment"),
                    decidedBy,
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("decided_at")));
        }
    }
}
