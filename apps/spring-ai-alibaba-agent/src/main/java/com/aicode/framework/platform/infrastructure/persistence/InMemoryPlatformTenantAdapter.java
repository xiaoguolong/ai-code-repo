package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内租户存储（Week 21，{@code platform.persistence.mode=memory}）。
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformTenantAdapter implements PlatformTenantPort {

    private final ConcurrentHashMap<String, PlatformTenant> store = new ConcurrentHashMap<>();

    @Override
    public Optional<PlatformTenant> findByKey(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(tenantKey.trim()));
    }

    @Override
    public List<PlatformTenant> listAll() {
        return store.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public void save(PlatformTenant tenant) {
        store.put(tenant.tenantKey(), tenant);
    }

    /**
     * @return seed 是否已写入（供初始化器判空）
     */
    public boolean isEmpty() {
        return store.isEmpty();
    }
}
