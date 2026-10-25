package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内知识库绑定存储（Week 25，Week 27 加同步状态；{@code platform.persistence.mode=memory}）。
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryKnowledgeBindingAdapter implements KnowledgeBindingPort {

    private static final Comparator<KnowledgeBinding> BY_CREATED_DESC =
            Comparator.comparing(KnowledgeBinding::createdAt).reversed()
                    .thenComparing(KnowledgeBinding::bindingId, Comparator.reverseOrder());

    private final ConcurrentHashMap<String, KnowledgeBinding> store = new ConcurrentHashMap<>();

    @Override
    public void save(KnowledgeBinding binding) {
        store.put(binding.bindingId(), binding);
    }

    @Override
    public boolean updateSyncState(
            String bindingId,
            KnowledgeBindingSyncStatus syncStatus,
            Instant syncedAt
    ) {
        if (bindingId == null || bindingId.isBlank() || syncStatus == null) {
            return false;
        }
        KnowledgeBinding updated = store.computeIfPresent(bindingId.trim(), (id, existing) ->
                new KnowledgeBinding(
                        existing.bindingId(), existing.tenantKey(), existing.enterpriseUserId(),
                        existing.knowledgeBaseId(), existing.name(), existing.description(),
                        existing.enabled(), existing.createdBy(), existing.createdAt(),
                        existing.updatedAt(), syncStatus, syncedAt));
        return updated != null;
    }

    @Override
    public Optional<KnowledgeBinding> findByTenantAndKnowledgeBase(String tenantKey, long knowledgeBaseId) {
        return store.values().stream()
                .filter(binding -> binding.tenantKey().equals(tenantKey))
                .filter(binding -> binding.knowledgeBaseId() == knowledgeBaseId)
                .findFirst();
    }

    @Override
    public Optional<KnowledgeBinding> findById(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(bindingId.trim()));
    }

    @Override
    public List<KnowledgeBinding> listByTenant(String tenantKey) {
        return store.values().stream()
                .filter(binding -> binding.tenantKey().equals(tenantKey))
                .sorted(BY_CREATED_DESC)
                .toList();
    }

    @Override
    public List<KnowledgeBinding> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(KnowledgeBinding::tenantKey)
                        .thenComparing(BY_CREATED_DESC))
                .toList();
    }

    @Override
    public long countBySyncStatus(KnowledgeBindingSyncStatus syncStatus) {
        if (syncStatus == null) {
            return 0L;
        }
        return store.values().stream()
                .filter(binding -> binding.syncStatus() == syncStatus)
                .count();
    }

    @Override
    public boolean delete(String tenantKey, String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return false;
        }
        KnowledgeBinding existing = store.get(bindingId.trim());
        if (existing == null || !existing.tenantKey().equals(tenantKey)) {
            return false;
        }
        store.remove(bindingId.trim());
        return true;
    }

    /**
     * @return 当前存储条数（测试断言用）
     */
    public int size() {
        return store.size();
    }
}
