package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内平台角色存储（Week 13 V1，Week 16 起仅在 {@code platform.persistence.mode=memory} 生效；
 * Week 23 起按 {@code tenantKey + roleKey} 隔离）。
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformRoleAdapter implements PlatformRolePort {

    private final ConcurrentHashMap<String, PlatformRole> store = new ConcurrentHashMap<>();

    @Override
    public Optional<PlatformRole> findByKey(String tenantKey, String roleKey) {
        if (tenantKey == null || tenantKey.isBlank() || roleKey == null || roleKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(key(tenantKey, roleKey)));
    }

    @Override
    public List<PlatformRole> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(PlatformRole::tenantKey)
                        .thenComparing(PlatformRole::roleKey))
                .toList();
    }

    @Override
    public void save(PlatformRole role) {
        store.put(key(role.tenantKey(), role.roleKey()), role);
    }

    /** seed 是否已写入（default 租户口径）。 */
    public boolean isEmpty() {
        return store.keySet().stream().noneMatch(k -> k.startsWith(PlatformTenant.DEFAULT_TENANT_KEY + "|"));
    }

    private String key(String tenantKey, String roleKey) {
        return tenantKey.trim() + "|" + roleKey.trim();
    }
}
