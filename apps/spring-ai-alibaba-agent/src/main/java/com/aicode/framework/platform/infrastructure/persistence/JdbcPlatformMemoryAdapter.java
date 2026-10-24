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
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆落库适配器（Week 21，表 {@code platform_memory}，迁移 V5）。
 *
 * <p><b>隔离在 SQL 谓词里</b>：每条查询都带 {@code tenant_key = ? AND user_id = ?}，
 * 排序与检索只在已过滤的候选集上进行，避免「先查全表再在内存过滤」这类容易漏分支的写法。</p>
 *
 * <p>向量以 JSON 文本存储（{@code [1.0,-0.5]}），检索时在应用侧算余弦。
 * 存储形态被本适配器封闭，后续换 pgvector 只改这一个类（见 Spec 七）。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformMemoryAdapter implements PlatformMemoryPort {

    private static final String SELECT_COLUMNS = """
            SELECT memory_id, tenant_key, user_id, session_id, task, answer, embedding, created_at
            FROM platform_memory
            """;

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
                UPDATE platform_memory SET session_id = ?, task = ?, answer = ?, embedding = ?, created_at = ?
                WHERE memory_id = ?
                """, record.sessionId(), record.task(), record.answer(), payload,
                PlatformJdbcSupport.toTimestamp(record.createdAt()), record.memoryId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_memory (
                        memory_id, tenant_key, user_id, session_id, task, answer, embedding, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, record.memoryId(), record.tenantKey(), record.userId(), record.sessionId(),
                    record.task(), record.answer(), payload,
                    PlatformJdbcSupport.toTimestamp(record.createdAt()));
        }
    }

    @Override
    public List<PlatformMemoryRecord> listRecent(PlatformMemoryScope scope, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ? ORDER BY created_at DESC, memory_id DESC LIMIT ?",
                recordMapper, scope.tenantKey(), scope.userId(), Math.max(limit, 0));
    }

    @Override
    public List<PlatformMemoryRecord> listBySession(PlatformMemoryScope scope, String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ? AND session_id = ?"
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
                SELECT_COLUMNS + " WHERE tenant_key = ? AND user_id = ?"
                        + " ORDER BY created_at DESC, memory_id DESC LIMIT ?",
                vectorMapper, scope.tenantKey(), scope.userId(), Math.max(candidateLimit, 0));
        return PlatformMemoryRanker.rank(candidates, queryVector, topK, minScore);
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
                PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")));
    }

    /**
     * 向量 ↔ JSON 文本编解码。空/非法值一律按空向量处理，避免一条脏数据打断整次检索。
     */
    static final class PlatformVectorCodec {

        private PlatformVectorCodec() {
        }

        static String encode(float[] vector) {
            if (vector == null || vector.length == 0) {
                return "";
            }
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(vector[i]);
            }
            return builder.append(']').toString();
        }

        static float[] decode(String payload) {
            if (payload == null || payload.isBlank()) {
                return new float[0];
            }
            String body = payload.trim();
            if (body.startsWith("[")) {
                body = body.substring(1);
            }
            if (body.endsWith("]")) {
                body = body.substring(0, body.length() - 1);
            }
            if (body.isBlank()) {
                return new float[0];
            }
            String[] parts = body.split(",");
            List<Float> values = new ArrayList<>(parts.length);
            for (String part : parts) {
                try {
                    values.add(Float.parseFloat(part.trim()));
                } catch (NumberFormatException ex) {
                    return new float[0];
                }
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i);
            }
            return vector;
        }
    }
}
