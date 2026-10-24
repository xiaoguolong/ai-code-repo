package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.PlatformMemoryRanker;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformMemoryVector;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内长期记忆存储（Week 21，{@code platform.persistence.mode=memory}）。
 *
 * <p>隔离在这里同样是硬约束：所有读方法都要求 scope，先按
 * {@code tenantKey + userId} 过滤再排序/检索。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformMemoryAdapter implements PlatformMemoryPort {

    private final ConcurrentHashMap<String, PlatformMemoryVector> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlatformMemoryRecord record, float[] embedding) {
        store.put(record.memoryId(), new PlatformMemoryVector(record,
                embedding == null ? new float[0] : embedding));
    }

    @Override
    public List<PlatformMemoryRecord> listRecent(PlatformMemoryScope scope, int limit) {
        return scoped(scope).stream()
                .sorted(Comparator.comparing((PlatformMemoryRecord record) -> record.createdAt()).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<PlatformMemoryRecord> listBySession(PlatformMemoryScope scope, String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        String normalized = sessionId.trim();
        return scoped(scope).stream()
                .filter(record -> normalized.equals(record.sessionId()))
                .sorted(Comparator.comparing((PlatformMemoryRecord record) -> record.createdAt()).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<PlatformMemoryHit> search(
            PlatformMemoryScope scope,
            float[] queryVector,
            int candidateLimit,
            int topK,
            double minScore
    ) {
        List<PlatformMemoryVector> candidates = store.values().stream()
                .filter(vector -> inScope(scope, vector.record()))
                .sorted(Comparator.comparing((PlatformMemoryVector vector) -> vector.record().createdAt()).reversed())
                .limit(Math.max(candidateLimit, 0))
                .toList();
        return PlatformMemoryRanker.rank(candidates, queryVector, topK, minScore);
    }

    private List<PlatformMemoryRecord> scoped(PlatformMemoryScope scope) {
        return store.values().stream()
                .map(PlatformMemoryVector::record)
                .filter(record -> inScope(scope, record))
                .toList();
    }

    private boolean inScope(PlatformMemoryScope scope, PlatformMemoryRecord record) {
        return scope.tenantKey().equals(record.tenantKey()) && scope.userId() == record.userId();
    }

    /**
     * @return 当前存储条数（测试断言用）
     */
    public int size() {
        return store.size();
    }
}
