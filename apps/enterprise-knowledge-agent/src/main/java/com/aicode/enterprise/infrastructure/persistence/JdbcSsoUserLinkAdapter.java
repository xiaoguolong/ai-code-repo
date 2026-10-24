package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SSO 映射表落库适配器（Week 21，表 {@code sso_user_link}，迁移 V7）。
 *
 * <p>「先 UPDATE 影响 0 行再 INSERT」：PostgreSQL 与 H2 的 upsert 语法不同（规范 3.7.1），
 * 这样同一套代码两个库都能跑。</p>
 */
@Component
public class JdbcSsoUserLinkAdapter implements SsoUserLinkPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSsoUserLinkAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findEnterpriseUserId(long platformUserId) {
        List<Long> found = jdbcTemplate.query(
                "SELECT enterprise_user_id FROM sso_user_link WHERE platform_user_id = ?",
                (rs, rowNum) -> rs.getLong("enterprise_user_id"),
                platformUserId);
        return found.stream().findFirst();
    }

    @Override
    public void link(long platformUserId, long enterpriseUserId, String tenantKey) {
        int updated = jdbcTemplate.update(
                "UPDATE sso_user_link SET enterprise_user_id = ?, tenant_key = ? WHERE platform_user_id = ?",
                enterpriseUserId, tenantKey == null ? "" : tenantKey, platformUserId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sso_user_link (platform_user_id, enterprise_user_id, tenant_key, linked_at)
                    VALUES (?, ?, ?, ?)
                    """, platformUserId, enterpriseUserId, tenantKey == null ? "" : tenantKey,
                    Timestamp.from(Instant.now()));
        }
    }
}
