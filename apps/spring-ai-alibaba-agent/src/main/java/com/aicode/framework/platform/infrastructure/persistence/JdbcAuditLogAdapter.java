package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 审计日志落库适配器（Week 16，{@code platform.persistence.mode=jdbc}）。
 *
 * <p>只写标识与结果，不写密码 / token / API Key；{@code detail} 超长自动截断。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcAuditLogAdapter implements AuditLogPort {

    private static final String INSERT_SQL = """
            INSERT INTO audit_log (user_id, action, resource, result, trace_id, detail, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_COLUMNS = """
            SELECT user_id, action, resource, result, trace_id, detail, created_at FROM audit_log
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AuditLogEntry> rowMapper = new AuditLogRowMapper();

    public JdbcAuditLogAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void record(AuditLogEntry entry) {
        jdbcTemplate.update(INSERT_SQL,
                entry.userId(),
                entry.action().name(),
                entry.resource(),
                entry.result().name(),
                entry.traceId() == null ? "" : entry.traceId(),
                truncate(entry.detail()),
                PlatformJdbcSupport.toTimestamp(entry.createdAt()));
    }

    @Override
    public List<AuditLogEntry> listByUserId(long userId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE user_id = ? ORDER BY created_at DESC, id DESC", rowMapper, userId);
    }

    @Override
    public List<AuditLogEntry> listAll() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " ORDER BY created_at DESC, id DESC", rowMapper);
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= AuditLogEntry.MAX_DETAIL_LENGTH
                ? detail
                : detail.substring(0, AuditLogEntry.MAX_DETAIL_LENGTH);
    }

    /**
     * {@code audit_log} 行 → 领域条目。{@code auditId} 未在查询中取回，统一置 0。
     */
    private static final class AuditLogRowMapper implements RowMapper<AuditLogEntry> {

        @Override
        public AuditLogEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuditLogEntry(
                    0L,
                    rs.getLong("user_id"),
                    AuditAction.valueOf(rs.getString("action")),
                    rs.getString("resource"),
                    AuditResult.valueOf(rs.getString("result")),
                    rs.getString("trace_id"),
                    rs.getString("detail"),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        }
    }
}
