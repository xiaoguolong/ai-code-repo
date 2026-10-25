package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SSO 映射表落库适配器（Week 21，表 {@code sso_user_link}，迁移 V7；Week 23 增加运维查询/解绑）。
 *
 * <p>「先 UPDATE 影响 0 行再 INSERT」：PostgreSQL 与 H2 的 upsert 语法不同（规范 3.7.1），
 * 这样同一套代码两个库都能跑。</p>
 */
@Component
public class JdbcSsoUserLinkAdapter implements SsoUserLinkPort {

    private static final String SELECT_COLUMNS = """
            SELECT platform_user_id, enterprise_user_id, tenant_key, linked_at
            FROM sso_user_link
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SsoUserLink> rowMapper = new SsoUserLinkRowMapper();

    public JdbcSsoUserLinkAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SsoUserLink> findByPlatformUserId(long platformUserId) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + " WHERE platform_user_id = ?", rowMapper, platformUserId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<SsoUserLink> findByEnterpriseUserId(long enterpriseUserId) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + " WHERE enterprise_user_id = ?", rowMapper, enterpriseUserId)
                .stream()
                .findFirst();
    }

    @Override
    public List<SsoUserLink> listAll(int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " ORDER BY linked_at DESC, platform_user_id DESC LIMIT ?",
                rowMapper, Math.max(limit, 0));
    }

    @Override
    public void link(long platformUserId, long enterpriseUserId, String tenantKey) {
        String normalizedTenant = tenantKey == null ? "" : tenantKey;
        int updated = jdbcTemplate.update(
                "UPDATE sso_user_link SET enterprise_user_id = ?, tenant_key = ?, linked_at = ?"
                        + " WHERE platform_user_id = ?",
                enterpriseUserId, normalizedTenant, Timestamp.from(Instant.now()), platformUserId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sso_user_link (platform_user_id, enterprise_user_id, tenant_key, linked_at)
                    VALUES (?, ?, ?, ?)
                    """, platformUserId, enterpriseUserId, normalizedTenant,
                    Timestamp.from(Instant.now()));
        }
    }

    @Override
    public boolean unlink(long platformUserId) {
        return jdbcTemplate.update(
                "DELETE FROM sso_user_link WHERE platform_user_id = ?", platformUserId) > 0;
    }

    /**
     * {@code sso_user_link} 行 → 领域模型。
     */
    private static final class SsoUserLinkRowMapper implements RowMapper<SsoUserLink> {

        @Override
        public SsoUserLink mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp linkedAt = rs.getTimestamp("linked_at");
            return new SsoUserLink(
                    rs.getLong("platform_user_id"),
                    rs.getLong("enterprise_user_id"),
                    rs.getString("tenant_key"),
                    linkedAt == null ? null : linkedAt.toInstant());
        }
    }
}
