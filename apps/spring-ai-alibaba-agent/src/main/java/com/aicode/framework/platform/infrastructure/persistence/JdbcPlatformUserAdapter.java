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
import java.util.List;
import java.util.Optional;

/**
 * 平台用户落库适配器（Week 16，替换 Week 13 内存实现）。
 *
 * <p>表结构见 {@code db/migration/V1__platform_rbac.sql}；{@link #save} 语义为
 * 「不存在则插入，存在则覆盖」，与内存实现保持一致，便于 seed 重复执行。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformUserAdapter implements PlatformUserPort {

    private static final String SELECT_COLUMNS =
            "SELECT id, username, password_hash, role_key FROM platform_user";

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
                UPDATE platform_user SET username = ?, password_hash = ?, role_key = ?
                WHERE id = ?
                """, user.username(), user.passwordHash(), user.roleKey(), user.id());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_user (id, username, password_hash, role_key, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, user.id(), user.username(), user.passwordHash(), user.roleKey(),
                    PlatformJdbcSupport.toTimestamp(java.time.Instant.now()));
        }
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
                    rs.getString("role_key"));
        }
    }
}
