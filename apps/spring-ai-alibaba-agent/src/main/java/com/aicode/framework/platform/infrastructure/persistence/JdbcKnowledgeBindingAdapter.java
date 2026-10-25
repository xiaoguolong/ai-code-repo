package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
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
 * 知识库绑定落库适配器（Week 25，表 {@code platform_knowledge_binding}，迁移 V9）。
 */
@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcKnowledgeBindingAdapter implements KnowledgeBindingPort {

    private static final String SELECT_COLUMNS = """
            SELECT binding_id, tenant_key, enterprise_user_id, knowledge_base_id, name, description,
                   enabled, created_by, created_at, updated_at
            FROM platform_knowledge_binding
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<KnowledgeBinding> rowMapper = new KnowledgeBindingRowMapper();

    public JdbcKnowledgeBindingAdapter(PlatformJdbcSupport jdbcSupport) {
        this.jdbcTemplate = jdbcSupport.jdbc();
    }

    @Override
    public void save(KnowledgeBinding binding) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_knowledge_binding SET
                    tenant_key = ?, enterprise_user_id = ?, knowledge_base_id = ?, name = ?,
                    description = ?, enabled = ?, created_by = ?, created_at = ?, updated_at = ?
                WHERE binding_id = ?
                """,
                binding.tenantKey(), binding.enterpriseUserId(), binding.knowledgeBaseId(),
                binding.name(), binding.description(), binding.enabled(), binding.createdBy(),
                PlatformJdbcSupport.toTimestamp(binding.createdAt()),
                PlatformJdbcSupport.toTimestamp(binding.updatedAt()),
                binding.bindingId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO platform_knowledge_binding (
                        binding_id, tenant_key, enterprise_user_id, knowledge_base_id, name, description,
                        enabled, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    binding.bindingId(), binding.tenantKey(), binding.enterpriseUserId(),
                    binding.knowledgeBaseId(), binding.name(), binding.description(), binding.enabled(),
                    binding.createdBy(),
                    PlatformJdbcSupport.toTimestamp(binding.createdAt()),
                    PlatformJdbcSupport.toTimestamp(binding.updatedAt()));
        }
    }

    @Override
    public Optional<KnowledgeBinding> findByTenantAndKnowledgeBase(String tenantKey, long knowledgeBaseId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? AND knowledge_base_id = ?",
                rowMapper, tenantKey, knowledgeBaseId).stream().findFirst();
    }

    @Override
    public Optional<KnowledgeBinding> findById(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE binding_id = ?", rowMapper, bindingId.trim())
                .stream().findFirst();
    }

    @Override
    public List<KnowledgeBinding> listByTenant(String tenantKey) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE tenant_key = ? ORDER BY created_at DESC, binding_id DESC",
                rowMapper, tenantKey);
    }

    @Override
    public boolean delete(String tenantKey, String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update(
                "DELETE FROM platform_knowledge_binding WHERE tenant_key = ? AND binding_id = ?",
                tenantKey, bindingId.trim()) > 0;
    }

    /**
     * {@code platform_knowledge_binding} 行 → 领域模型。
     */
    private static final class KnowledgeBindingRowMapper implements RowMapper<KnowledgeBinding> {

        @Override
        public KnowledgeBinding mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new KnowledgeBinding(
                    rs.getString("binding_id"),
                    rs.getString("tenant_key"),
                    rs.getLong("enterprise_user_id"),
                    rs.getLong("knowledge_base_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBoolean("enabled"),
                    rs.getLong("created_by"),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("created_at")),
                    PlatformJdbcSupport.toInstant(rs.getTimestamp("updated_at")));
        }
    }
}
