package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.PlatformMemoryRanker;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformMemoryVector;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * 长期记忆落库适配器（Week 21，表 {@code platform_memory}，迁移 V5；Week 23 迁移 V6 加生命周期字段）。
 *
 * <p><b>隔离在 SQL 谓词里</b>：每条读查询都带 {@code tenant_key = ? AND user_id = ?}，
 * 排序与检索只在已过滤的候选集上进行，避免「先查全表再在内存过滤」这类容易漏分支的写法。</p>
 *
 * <p>Week 23：读路径同时过滤 {@code expires_at <= CURRENT_TIMESTAMP}；删除带完整 scope；
 * 向量重建是唯一不带 scope 的运维查询（{@link #listStaleEmbeddings}），调用方必须先做 admin 校验。</p>
 *
 * <p>向量以 JSON 文本存储（{@code [1.0,-0.5]}），检索时在应用侧算余弦。
 * 存储形态被本适配器封闭，后续换 pgvector 只改这一个类（见 Spec 七）。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformMemoryAdapter implements PlatformMemoryPort {

    private static final String SELECT_COLUMNS = """
            SELECT memory_id, tenant_key, user_id, session_id, task, answer, embedding,
                   created_at, expires_at, embedding_model
            FROM platform_memory
            """;

    /** 未过期条件：null = 永不过期。 */
    private static final String NOT_EXPIRED = " (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformMemoryRecord> recordMapper = new PlatformMemoryRowMapper();
    private final RowMapper<PlatformMemoryVector> vectorMapper = new PlatformMemoryVectorRowMapper();

    public JdbcPlatformMemoryAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void save(PlatformMemoryRecord record, float[] embedding) {
        String payload = PlatformVectorCodec.encode(embedding);
        int updated = jdbcTemplate.update("""
                UPDATE platform_memory SET session_id = ?, task = ?, answer = ?, embedding = ?,
                    created_at = ?, expires_at = ?, embedding_model = ?
                WHERE memory_id = ?
                """, record.sessionId(), record.task(), record.answer(), payload,
                PlatformJdbcSupport.toTimestamp(record.createdAt()),
                PlatformJdbcSupport.toTimestamp(record.expiresAt()),
                record.embeddingModel(), record.memoryId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_memory (
                        memory_id, tenant_key, user_id, session_id, task, answer, embedding,
                        created_at, expires_at, embedding_model)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, record.memoryId(), record.tenantKey(), record.userId(), record.sessionId(),
                    record.task(), record.answer(), payload,
                    PlatformJdbcSupport.toTimestamp(record.createdAt()),
                    PlatformJdbcSupport.toTimestamp(record.expiresAt()),
                    record.embeddingModel());
        }
    }

    @Override
    public List<PlatformMemoryRecord> listRecent(PlatformMemoryScope scope, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ? AND" + NOT_EXPIRED
                        + " ORDER BY created_at DESC, memory_id DESC LIMIT ?",
                recordMapper, scope.tenantKey(), scope.userId(), Math.max(limit, 0));
    }

    @Override
    public List<PlatformMemoryRecord> listBySession(PlatformMemoryScope scope, String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ? AND session_id = ? AND" + NOT_EXPIRED
                        + " ORDER BY created_at DESC, memory_id DESC LIMIT ?",
                recordMapper, scope.tenantKey(), scope.userId(), sessionId.trim(), Math.max(limit, 0));
    }

    @Override
    public List<PlatformMemoryHit> search(
            PlatformMemoryScope scope,
            float[] queryVector,
            int candidateLimit,
            int topK,
            double minScore
    ) {
        List<PlatformMemoryVector> candidates = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ? AND" + NOT_EXPIRED
                        + " ORDER BY created_at DESC, memory_id DESC LIMIT ?",
                vectorMapper, scope.tenantKey(), scope.userId(), Math.max(candidateLimit, 0));
        return PlatformMemoryRanker.rank(candidates, queryVector, topK, minScore);
    }

    @Override
    public boolean delete(PlatformMemoryScope scope, String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update(
                "DELETE FROM platform_memory WHERE memory_id = ? AND tenant_key = ? AND user_id = ?",
                memoryId.trim(), scope.tenantKey(), scope.userId()) > 0;
    }

    @Override
    public int purgeExpired(Instant now, int limit) {
        if (now == null || limit <= 0) {
            return 0;
        }
        List<String> ids = jdbcTemplate.queryForList("""
                SELECT memory_id FROM platform_memory
                WHERE expires_at IS NOT NULL AND expires_at <= ?
                ORDER BY expires_at ASC LIMIT ?
                """, String.class, PlatformJdbcSupport.toTimestamp(now), limit);
        if (ids.isEmpty()) {
            return 0;
        }
        int[] affected = jdbcTemplate.batchUpdate(
                "DELETE FROM platform_memory WHERE memory_id = ?",
                ids.stream().map(id -> new Object[]{id}).toList());
        int deleted = 0;
        for (int rows : affected) {
            deleted += Math.max(rows, 0);
        }
        return deleted;
    }

    @Override
    public List<PlatformMemoryRecord> listStaleEmbeddings(String currentModel, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE embedding_model IS NULL OR embedding_model = ''"
                        + " OR embedding_model <> ? ORDER BY created_at ASC LIMIT ?",
                recordMapper, currentModel, limit);
    }

    @Override
    public boolean updateEmbedding(String memoryId, float[] embedding, String embeddingModel) {
        if (memoryId == null || memoryId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update(
                "UPDATE platform_memory SET embedding = ?, embedding_model = ? WHERE memory_id = ?",
                PlatformVectorCodec.encode(embedding), embeddingModel, memoryId.trim()) > 0;
    }

    /**
     * 只映射记录（向量列在列表查询中不使用，避免无谓解析）。
     */
    private static final class PlatformMemoryRowMapper implements RowMapper<PlatformMemoryRecord> {

        @Override
        public PlatformMemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return readRecord(rs);
        }
    }

    /**
     * 记录 + 向量（语义检索用）。
     */
    private static final class PlatformMemoryVectorRowMapper implements RowMapper<PlatformMemoryVector> {

        @Override
        public PlatformMemoryVector mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlatformMemoryVector(readRecord(rs), PlatformVectorCodec.decode(rs.getString("embedding")));
        }
    }

    private static PlatformMemoryRecord readRecord(ResultSet rs) throws SQLException {
        return new PlatformMemoryRecord(
                rs.getString("memory_id"),
                rs.getString("tenant_key"),
                rs.getLong("user_id"),
                rs.getString("session_id"),
                rs.getString("task"),
                rs.getString("answer"),
                PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")),
                PlatformJdbcSupport.toInstant(rs.getTimestamp("expires_at")),
                rs.getString("embedding_model"));
    }
}
