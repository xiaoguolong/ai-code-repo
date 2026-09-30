package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内平台角色存储（Week 13 V1）。
 */
@Component
public class InMemoryPlatformRoleAdapter implements PlatformRolePort {

    private final ConcurrentHashMap<String, PlatformRole> store = new ConcurrentHashMap<>();

    @Override
    public Optional<PlatformRole> findByKey(String roleKey) {
        if (roleKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(roleKey.trim()));
    }

    @Override
    public List<PlatformRole> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(PlatformRole::roleKey))
                .toList();
    }

    @Override
    public void save(PlatformRole role) {
        store.put(role.roleKey(), role);
    }

    /** seed 是否已写入。 */
    public boolean isEmpty() {
        return store.isEmpty();
    }
}
