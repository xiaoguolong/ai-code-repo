package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
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
 * 租户落库适配器（Week 21，表 {@code platform_tenant}，迁移 V4）。
 *
 * <p>{@link #save} 采用「存在则 UPDATE，否则 INSERT」，保证 seed 与迁移重复执行幂等。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformTenantAdapter implements PlatformTenantPort {

    private static final String SELECT_COLUMNS =
            "SELECT tenant_key, display_name, status, created_at FROM platform_tenant";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformTenant> rowMapper = new PlatformTenantRowMapper();

    public JdbcPlatformTenantAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public Optional<PlatformTenant> findByKey(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE tenant_key = ?", rowMapper, tenantKey.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<PlatformTenant> listAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY tenant_key", rowMapper);
    }

    @Override
    public void save(PlatformTenant tenant) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_tenant SET display_name = ?, status = ?
                WHERE tenant_key = ?
                """, tenant.displayName(), tenant.status().name(), tenant.tenantKey());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_tenant (tenant_key, display_name, status, created_at)
                    VALUES (?, ?, ?, ?)
                    """, tenant.tenantKey(), tenant.displayName(), tenant.status().name(),
                    PlatformJdbcSupport.toTimestamp(
                            tenant.createdAt() == null ? java.time.Instant.now() : tenant.createdAt()));
        }
    }

    /**
     * {@code platform_tenant} 行 → 领域模型。
     */
    private static final class PlatformTenantRowMapper implements RowMapper<PlatformTenant> {

        @Override
        public PlatformTenant mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlatformTenant(
                    rs.getString("tenant_key"),
                    rs.getString("display_name"),
                    TenantStatus.from(rs.getString("status")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        }
    }
}
