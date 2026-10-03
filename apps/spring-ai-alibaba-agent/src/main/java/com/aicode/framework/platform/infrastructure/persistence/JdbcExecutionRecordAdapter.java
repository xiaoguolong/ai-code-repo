package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
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
 * 执行记录落库适配器（Week 16，替换 Week 13 内存实现）。
 *
 * <p>同一次执行会先写 RUNNING、再覆盖为 COMPLETED / FAILED，因此 {@link #save}
 * 采用「存在则 UPDATE，否则 INSERT」的覆盖写语义，与内存实现保持一致。
 * 主键 {@code id} 由 {@link PlatformJdbcSupport#nextId} 生成，双库通用。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcExecutionRecordAdapter implements ExecutionRecordPort {

    private static final String SELECT_COLUMNS = """
            SELECT execution_id, user_id, agent_key, agent_type, status, input_json, output_json,
                   model, prompt_tokens, completion_tokens, total_tokens, error_message,
                   started_at, finished_at
            FROM execution_record
            """;

    private final PlatformJdbcSupport jdbcSupport;
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExecutionRecord> rowMapper = new ExecutionRecordRowMapper();

    public JdbcExecutionRecordAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcSupport = jdbcSupport;
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void save(ExecutionRecord record) {
        int updated = jdbcTemplate.update("""
                UPDATE execution_record SET
                    user_id = ?, agent_key = ?, agent_type = ?, status = ?, input_json = ?,
                    output_json = ?, model = ?, prompt_tokens = ?, completion_tokens = ?,
                    total_tokens = ?, error_message = ?, started_at = ?, finished_at = ?
                WHERE execution_id = ?
                """,
                record.userId(), record.agentKey(), record.agentType().name(), record.status().name(),
                record.inputJson(), record.outputJson(), nullToEmpty(record.model()),
                record.usage().promptTokens(), record.usage().completionTokens(), record.usage().totalTokens(),
                nullToEmpty(record.errorMessage()),
                PlatformJdbcSupport.toTimestamp(record.startedAt()),
                PlatformJdbcSupport.toTimestamp(record.finishedAt()),
                record.executionId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO execution_record (
                        id, execution_id, user_id, agent_key, agent_type, status, input_json,
                        output_json, model, prompt_tokens, completion_tokens, total_tokens,
                        error_message, started_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    jdbcSupport.nextId("execution_record", "id"),
                    record.executionId(), record.userId(), record.agentKey(), record.agentType().name(),
                    record.status().name(), record.inputJson(), record.outputJson(), nullToEmpty(record.model()),
                    record.usage().promptTokens(), record.usage().completionTokens(), record.usage().totalTokens(),
                    nullToEmpty(record.errorMessage()),
                    PlatformJdbcSupport.toTimestamp(record.startedAt()),
                    PlatformJdbcSupport.toTimestamp(record.finishedAt()));
        }
    }

    @Override
    public Optional<ExecutionRecord> findById(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE execution_id = ?", rowMapper, executionId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<ExecutionRecord> listAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY started_at DESC, id DESC", rowMapper);
    }

    @Override
    public List<ExecutionRecord> listByUserId(long userId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE user_id = ? ORDER BY started_at DESC, id DESC", rowMapper, userId);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * {@code execution_record} 行 → 领域模型。
     */
    private static final class ExecutionRecordRowMapper implements RowMapper<ExecutionRecord> {

        @Override
        public ExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instant finishedAt = PlatformJdbcSupport.toInstant(rs.getTimestamp("finished_at"));
            return new ExecutionRecord(
                    rs.getString("execution_id"),
                    rs.getLong("user_id"),
                    rs.getString("agent_key"),
                    AgentType.valueOf(rs.getString("agent_type")),
                    ExecutionStatus.valueOf(rs.getString("status")),
                    rs.getString("input_json"),
                    rs.getString("output_json"),
                    rs.getString("model"),
                    new TokenUsage(
                            rs.getInt("prompt_tokens"),
                            rs.getInt("completion_tokens"),
                            rs.getInt("total_tokens")),
                    rs.getString("error_message"),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("started_at")),
                    finishedAt);
        }
    }
}
