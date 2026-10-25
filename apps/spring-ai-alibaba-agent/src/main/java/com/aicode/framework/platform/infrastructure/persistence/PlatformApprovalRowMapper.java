package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@code platform_approval} 行 → 领域模型（Week 23，Week 27 抽为独立文件）。
 *
 * <p>只做列到 record 的映射；决策字段为 NULL 时 {@code decidedBy/decidedAt} 保持 null，
 * 表示仍待审。</p>
 */
final class PlatformApprovalRowMapper implements RowMapper<PlatformApproval> {

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
