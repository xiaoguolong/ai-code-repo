package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 平台角色落库适配器（Week 16，替换 Week 13 内存实现）。
 *
 * <p>角色主体在 {@code platform_role}，Agent / Tool / patientId 三类授权分别落在
 * {@code platform_role_agent} / {@code platform_role_tool} / {@code platform_role_patient}。
 * {@link #save} 为「先删后插」覆盖写：授权集合以本次入参为准，避免残留旧授权造成越权。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcPlatformRoleAdapter implements PlatformRolePort {

    private static final String SELECT_ROLES =
            "SELECT role_key, display_name, is_admin FROM platform_role";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformRole> partialMapper = new RoleHeaderRowMapper();

    public JdbcPlatformRoleAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public Optional<PlatformRole> findByKey(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        SELECT_ROLES + " WHERE role_key = ?", partialMapper, roleKey.trim())
                .stream()
                .findFirst()
                .map(this::withGrants);
    }

    @Override
    public List<PlatformRole> listAll() {
        return jdbcTemplate.query(SELECT_ROLES + " ORDER BY role_key", partialMapper)
                .stream()
                .map(this::withGrants)
                .toList();
    }

    @Override
    public void save(PlatformRole role) {
        int updated = jdbcTemplate.update(
                "UPDATE platform_role SET display_name = ?, is_admin = ? WHERE role_key = ?",
                role.displayName(), role.admin(), role.roleKey());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO platform_role (role_key, display_name, is_admin) VALUES (?, ?, ?)",
                    role.roleKey(), role.displayName(), role.admin());
        }
        replaceGrants("platform_role_agent", "agent_key", role.roleKey(), role.allowedAgentKeys());
        replaceGrants("platform_role_tool", "tool_key", role.roleKey(), role.allowedToolKeys());
        replaceGrants("platform_role_patient", "patient_id", role.roleKey(), role.allowedPatientIds());
    }

    /** 先清空该角色的同类授权，再按入参重建，实现覆盖写语义。 */
    private void replaceGrants(String table, String valueColumn, String roleKey, Set<String> values) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE role_key = ?", roleKey);
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : new TreeSet<>(values)) {
            jdbcTemplate.update(
                    "INSERT INTO " + table + " (role_key, " + valueColumn + ") VALUES (?, ?)",
                    roleKey, value);
        }
    }

    /** 补齐三类授权集合。 */
    private PlatformRole withGrants(PlatformRole header) {
        String roleKey = header.roleKey();
        return new PlatformRole(
                roleKey,
                header.displayName(),
                header.admin(),
                readGrants("platform_role_agent", "agent_key", roleKey),
                readGrants("platform_role_tool", "tool_key", roleKey),
                readGrants("platform_role_patient", "patient_id", roleKey));
    }

    private Set<String> readGrants(String table, String valueColumn, String roleKey) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT " + valueColumn + " FROM " + table + " WHERE role_key = ?",
                String.class, roleKey));
    }

    /**
     * 角色主体行 → 不含授权的角色（授权由 {@link #withGrants} 补齐）。
     */
    private static final class RoleHeaderRowMapper implements RowMapper<PlatformRole> {

        @Override
        public PlatformRole mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlatformRole(
                    rs.getString("role_key"),
                    rs.getString("display_name"),
                    rs.getBoolean("is_admin"),
                    Set.of(),
                    Set.of(),
                    Set.of());
        }
    }
}
