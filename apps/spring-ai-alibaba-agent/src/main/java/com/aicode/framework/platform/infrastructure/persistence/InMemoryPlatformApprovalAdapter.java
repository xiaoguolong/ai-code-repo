package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内审批任务存储（Week 23，{@code platform.persistence.mode=memory}）。
 *
 * <p>{@link #markDecided} 用 {@link ConcurrentHashMap#computeIfPresent} 做原子迁移，
 * 与 JDBC 适配器的 {@code WHERE status='PENDING'} 语义一致。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformApprovalAdapter implements PlatformApprovalPort {

    private final ConcurrentHashMap<String, PlatformApproval> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlatformApproval approval) {
        store.put(approval.approvalId(), approval);
    }

    @Override
    public Optional<PlatformApproval> findById(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(approvalId.trim()));
    }

    @Override
    public List<PlatformApproval> listByUser(long userId, ApprovalStatus status, int limit) {
        return filter(status, limit, approval -> approval.userId() == userId);
    }

    @Override
    public List<PlatformApproval> listAll(ApprovalStatus status, int limit) {
        return filter(status, limit, approval -> true);
    }

    @Override
    public long countAll(ApprovalStatus status) {
        return count(status, approval -> true);
    }

    @Override
    public long countByUser(long userId, ApprovalStatus status) {
        return count(status, approval -> approval.userId() == userId);
    }

    @Override
    public boolean markDecided(
            String approvalId,
            ApprovalStatus status,
            long decidedBy,
            String comment,
            Instant decidedAt
    ) {
        if (approvalId == null || approvalId.isBlank()) {
            return false;
        }
        AtomicBoolean changed = new AtomicBoolean(false);
        store.computeIfPresent(approvalId.trim(), (id, existing) -> {
            if (!existing.pending()) {
                return existing;
            }
            changed.set(true);
            return new PlatformApproval(
                    existing.approvalId(), existing.executionId(), existing.tenantKey(), existing.userId(),
                    existing.agentKey(), existing.patientId(), existing.riskLevel(), existing.task(),
                    status, comment, decidedBy, existing.createdAt(), decidedAt);
        });
        return changed.get();
    }

    private List<PlatformApproval> filter(
            ApprovalStatus status,
            int limit,
            java.util.function.Predicate<PlatformApproval> predicate
    ) {
        if (status == null || limit <= 0) {
            return List.of();
        }
        return store.values().stream()
                .filter(predicate)
                .filter(approval -> approval.status() == status)
                .sorted(Comparator.comparing(PlatformApproval::createdAt).reversed()
                        .thenComparing(PlatformApproval::approvalId, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private long count(ApprovalStatus status, java.util.function.Predicate<PlatformApproval> predicate) {
        if (status == null) {
            return 0L;
        }
        return store.values().stream()
                .filter(predicate)
                .filter(approval -> approval.status() == status)
                .count();
    }

    /**
     * @return 当前存储条数（测试断言用）
     */
    public int size() {
        return store.size();
    }
}
