package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
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
 * 平台用户落库适配器（Week 16；Week 21 加 {@code tenant_key}；Week 28 加 {@code disabled_at}）。
 *
 * <p>表结构见 V1/V4/V11。{@link #save} 语义为「不存在则插入，存在则覆盖身份字段」，
 * 但**绝不覆盖 {@code disabled_at}**：启动 seed 重跑不能把停用用户复活。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformUserAdapter implements PlatformUserPort {

    private static final String SELECT_COLUMNS =
            "SELECT id, username, password_hash, role_key, tenant_key, disabled_at FROM platform_user";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformUser> rowMapper = new PlatformUserRowMapper();

    public JdbcPlatformUserAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public Optional<PlatformUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<PlatformUser> found = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE username = ?", rowMapper, username.trim());
        return found.stream().findFirst();
    }

    @Override
    public Optional<PlatformUser> findById(long userId) {
        List<PlatformUser> found = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = ?", rowMapper, userId);
        return found.stream().findFirst();
    }

    @Override
    public void save(PlatformUser user) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_user SET username = ?, password_hash = ?, role_key = ?, tenant_key = ?
                WHERE id = ?
                """, user.username(), user.passwordHash(), user.roleKey(), user.tenantKey(), user.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_user (id, username, password_hash, role_key, tenant_key, created_at, disabled_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, user.id(), user.username(), user.passwordHash(), user.roleKey(), user.tenantKey(),
                    PlatformJdbcSupport.toTimestamp(Instant.now()),
                    PlatformJdbcSupport.toTimestamp(user.disabledAt()));
        }
    }

    @Override
    public List<PlatformUser> listAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY id ASC", rowMapper);
    }

    @Override
    public boolean updateDisabledAt(long userId, Instant disabledAt) {
        return jdbcTemplate.update(
                "UPDATE platform_user SET disabled_at = ? WHERE id = ?",
                PlatformJdbcSupport.toTimestamp(disabledAt), userId) > 0;
    }

    /**
     * {@code platform_user} 行 → 领域模型。
     */
    private static final class PlatformUserRowMapper implements RowMapper<PlatformUser> {

        @Override
        public PlatformUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlatformUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("role_key"),
                    rs.getString("tenant_key"),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("disabled_at")));
        }
    }
}
