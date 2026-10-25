package com.aicode.framework.followup.infrastructure.persistence;

import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.domain.port.FollowUpTaskPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内随访任务存储（Week 24，{@code platform.persistence.mode=memory}）。
 *
 * <p>{@link #markStatus} 用 {@link ConcurrentHashMap#computeIfPresent} 做原子迁移，
 * 与 JDBC 适配器的 {@code WHERE status='PENDING'} 语义一致。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryFollowUpTaskAdapter implements FollowUpTaskPort {

    private final ConcurrentHashMap<String, FollowUpTask> store = new ConcurrentHashMap<>();

    @Override
    public void save(FollowUpTask task) {
        store.put(task.taskId(), task);
    }

    @Override
    public Optional<FollowUpTask> findById(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(taskId.trim()));
    }

    @Override
    public List<FollowUpTask> list(
            String tenantKey, long userId, FollowUpStatus status, String patientId, int limit) {
        String normalizedPatient = patientId == null ? "" : patientId.trim();
        return store.values().stream()
                .filter(task -> task.tenantKey().equals(tenantKey) && task.userId() == userId)
                .filter(task -> status == null || task.status() == status)
                .filter(task -> normalizedPatient.isEmpty() || task.patientId().equals(normalizedPatient))
                .sorted(Comparator.comparing(FollowUpTask::dueAt).thenComparing(FollowUpTask::taskId))
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<FollowUpTask> listPendingDue(
            String tenantKey, long userId, Instant fromInclusive, Instant toExclusive, int limit) {
        return store.values().stream()
                .filter(task -> task.tenantKey().equals(tenantKey) && task.userId() == userId)
                .filter(task -> task.status() == FollowUpStatus.PENDING)
                .filter(task -> fromInclusive == null || !task.dueAt().isBefore(fromInclusive))
                .filter(task -> toExclusive == null || task.dueAt().isBefore(toExclusive))
                .sorted(Comparator.comparing(FollowUpTask::dueAt).thenComparing(FollowUpTask::taskId))
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<FollowUpTask> listByApproval(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return List.of();
        }
        String normalized = approvalId.trim();
        return store.values().stream()
                .filter(task -> normalized.equals(task.approvalId()))
                .sorted(Comparator.comparing(FollowUpTask::dueAt).thenComparing(FollowUpTask::taskId))
                .toList();
    }

    @Override
    public boolean markStatus(
            String taskId,
            String tenantKey,
            long userId,
            FollowUpStatus target,
            String note,
            Instant updatedAt,
            Instant completedAt) {
        if (taskId == null || taskId.isBlank() || target == null || target == FollowUpStatus.PENDING) {
            return false;
        }
        AtomicBoolean changed = new AtomicBoolean(false);
        store.computeIfPresent(taskId.trim(), (id, existing) -> {
            if (!existing.pending()
                    || !existing.tenantKey().equals(tenantKey)
                    || existing.userId() != userId) {
                return existing;
            }
            changed.set(true);
            return new FollowUpTask(
                    existing.taskId(), existing.tenantKey(), existing.userId(), existing.patientId(),
                    existing.source(), existing.approvalId(), existing.executionId(),
                    existing.title(), existing.detail(), existing.priority(),
                    target, existing.dueAt(), existing.createdAt(), updatedAt,
                    completedAt, note == null ? "" : note);
        });
        return changed.get();
    }

    @Override
    public long countByStatus(String tenantKey, long userId, FollowUpStatus status) {
        return store.values().stream()
                .filter(task -> task.tenantKey().equals(tenantKey) && task.userId() == userId)
                .filter(task -> task.status() == status)
                .count();
    }

    /**
     * @return 当前存储条数（测试断言用）
     */
    public int size() {
        return store.size();
    }
}
