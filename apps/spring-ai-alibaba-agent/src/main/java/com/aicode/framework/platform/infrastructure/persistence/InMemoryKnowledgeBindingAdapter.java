package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内知识库绑定存储（Week 25，{@code platform.persistence.mode=memory}）。
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryKnowledgeBindingAdapter implements KnowledgeBindingPort {

    private final ConcurrentHashMap<String, KnowledgeBinding> store = new ConcurrentHashMap<>();

    @Override
    public void save(KnowledgeBinding binding) {
        store.put(binding.bindingId(), binding);
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
                .sorted(Comparator.comparing(KnowledgeBinding::createdAt).reversed()
                        .thenComparing(KnowledgeBinding::bindingId, Comparator.reverseOrder()))
                .toList();
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
