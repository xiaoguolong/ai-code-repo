package com.aicode.framework.followup.infrastructure.persistence;

import com.aicode.framework.followup.domain.model.FollowUpPriority;
import com.aicode.framework.followup.domain.model.FollowUpSource;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import com.aicode.framework.platform.infrastructure.persistence.PlatformJdbcSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 随访任务落库适配器（Week 24，表 {@code platform_followup_task}，迁移 V8）。
 *
 * <p>状态迁移用单条 {@code UPDATE ... WHERE status='PENDING'}：完成与取消并发时只有一个赢家，
 * 另一个拿到 409，不会出现「已完成又被取消」。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcFollowUpTaskAdapter implements FollowUpTaskPort {

    private static final String SELECT_COLUMNS = """
            SELECT task_id, tenant_key, user_id, patient_id, source, approval_id, execution_id,
                   title, detail, priority, status, due_at, created_at, updated_at, completed_at, note
            FROM platform_followup_task
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<FollowUpTask> rowMapper = new FollowUpTaskRowMapper();

    public JdbcFollowUpTaskAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void save(FollowUpTask task) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_followup_task SET
                    tenant_key = ?, user_id = ?, patient_id = ?, source = ?, approval_id = ?,
                    execution_id = ?, title = ?, detail = ?, priority = ?, status = ?, due_at = ?,
                    created_at = ?, updated_at = ?, completed_at = ?, note = ?
                WHERE task_id = ?
                """,
                task.tenantKey(), task.userId(), task.patientId(), task.source().name(),
                task.approvalId(), task.executionId(), task.title(), task.detail(),
                task.priority().name(), task.status().name(),
                PlatformJdbcSupport.toTimestamp(task.dueAt()),
                PlatformJdbcSupport.toTimestamp(task.createdAt()),
                PlatformJdbcSupport.toTimestamp(task.updatedAt()),
                PlatformJdbcSupport.toTimestamp(task.completedAt()),
                task.note(), task.taskId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_followup_task (
                        task_id, tenant_key, user_id, patient_id, source, approval_id, execution_id,
                        title, detail, priority, status, due_at, created_at, updated_at, completed_at, note)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.taskId(), task.tenantKey(), task.userId(), task.patientId(),
                    task.source().name(), task.approvalId(), task.executionId(), task.title(),
                    task.detail(), task.priority().name(), task.status().name(),
                    PlatformJdbcSupport.toTimestamp(task.dueAt()),
                    PlatformJdbcSupport.toTimestamp(task.createdAt()),
                    PlatformJdbcSupport.toTimestamp(task.updatedAt()),
                    PlatformJdbcSupport.toTimestamp(task.completedAt()),
                    task.note());
        }
    }

    @Override
    public Optional<FollowUpTask> findById(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE task_id = ?", rowMapper, taskId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<FollowUpTask> list(
            String tenantKey, long userId, FollowUpStatus status, String patientId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append(" WHERE tenant_key = ? AND user_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenantKey, userId));
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" AND patient_id = ?");
            args.add(patientId.trim());
        }
        sql.append(" ORDER BY due_at ASC, task_id ASC LIMIT ?");
        args.add(Math.max(limit, 0));
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    @Override
    public List<FollowUpTask> listPendingDue(
            String tenantKey, long userId, Instant fromInclusive, Instant toExclusive, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append(" WHERE tenant_key = ? AND user_id = ? AND status = 'PENDING'");
        List<Object> args = new ArrayList<>(List.of(tenantKey, userId));
        if (fromInclusive != null) {
            sql.append(" AND due_at >= ?");
            args.add(PlatformJdbcSupport.toTimestamp(fromInclusive));
        }
        if (toExclusive != null) {
            sql.append(" AND due_at < ?");
            args.add(PlatformJdbcSupport.toTimestamp(toExclusive));
        }
        sql.append(" ORDER BY due_at ASC, task_id ASC LIMIT ?");
        args.add(Math.max(limit, 0));
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    @Override
    public List<FollowUpTask> listByApproval(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE approval_id = ? ORDER BY due_at ASC, task_id ASC",
                rowMapper, approvalId.trim());
    }

    @Override
    public boolean markStatus(
            String taskId,
            String tenantKey,
            long userId,
            FollowUpStatus target,
            String note,
            Instant updatedAt,
            Instant completedAt) {
        if (taskId == null || taskId.isBlank() || target == null || target == FollowUpStatus.PENDING) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE platform_followup_task
                SET status = ?, note = ?, updated_at = ?, completed_at = ?
                WHERE task_id = ? AND tenant_key = ? AND user_id = ? AND status = 'PENDING'
                """,
                target.name(), note == null ? "" : note,
                PlatformJdbcSupport.toTimestamp(updatedAt),
                PlatformJdbcSupport.toTimestamp(completedAt),
                taskId.trim(), tenantKey, userId) > 0;
    }

    @Override
    public long countByStatus(String tenantKey, long userId, FollowUpStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_followup_task WHERE tenant_key = ? AND user_id = ? AND status = ?",
                Long.class, tenantKey, userId, status.name());
        return count == null ? 0L : count;
    }

    /**
     * {@code platform_followup_task} 行 → 领域模型。
     */
    private static final class FollowUpTaskRowMapper implements RowMapper<FollowUpTask> {

        @Override
        public FollowUpTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FollowUpTask(
                    rs.getString("task_id"),
                    rs.getString("tenant_key"),
                    rs.getLong("user_id"),
                    rs.getString("patient_id"),
                    FollowUpSource.valueOf(rs.getString("source")),
                    rs.getString("approval_id"),
                    rs.getString("execution_id"),
                    rs.getString("title"),
                    rs.getString("detail"),
                    FollowUpPriority.valueOf(rs.getString("priority")),
                    FollowUpStatus.valueOf(rs.getString("status")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("due_at")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("updated_at")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("completed_at")),
                    rs.getString("note"));
        }
    }
}
