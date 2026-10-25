package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.PlatformMemoryRanker;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformMemoryVector;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内长期记忆存储（Week 21，{@code platform.persistence.mode=memory}）。
 *
 * <p>隔离在这里同样是硬约束：所有读方法都要求 scope，先按
 * {@code tenantKey + userId} 过滤再排序/检索。Week 23 起读路径同时过滤过期记录，
 * 与 JDBC 适配器的 {@code expires_at > CURRENT_TIMESTAMP} 语义保持一致。</p>
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformMemoryAdapter implements PlatformMemoryPort {

    private final ConcurrentHashMap<String, PlatformMemoryVector> store = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * 测试便利构造器：使用系统 UTC 时钟。
     */
    public InMemoryPlatformMemoryAdapter() {
        this(Clock.systemUTC());
    }

    /**
     * Spring 注入构造器：过期判断与业务时钟同源（单测可注入固定时钟）。
     *
     * @param clock UTC 时钟
     */
    @Autowired
    public InMemoryPlatformMemoryAdapter(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void save(PlatformMemoryRecord record, float[] embedding) {
        store.put(record.memoryId(), new PlatformMemoryVector(record,
                embedding == null ? new float[0] : embedding));
    }

    @Override
    public List<PlatformMemoryRecord> listRecent(PlatformMemoryScope scope, int limit) {
        Instant now = clock.instant();
        return scoped(scope, now).stream()
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
        return scoped(scope, clock.instant()).stream()
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
        Instant now = clock.instant();
        List<PlatformMemoryVector> candidates = store.values().stream()
                .filter(vector -> inScope(scope, vector.record()))
                .filter(vector -> !vector.record().expiredAt(now))
                .sorted(Comparator.comparing((PlatformMemoryVector vector) -> vector.record().createdAt()).reversed())
                .limit(Math.max(candidateLimit, 0))
                .toList();
        return PlatformMemoryRanker.rank(candidates, queryVector, topK, minScore);
    }

    @Override
    public boolean delete(PlatformMemoryScope scope, String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return false;
        }
        PlatformMemoryVector existing = store.get(memoryId.trim());
        if (existing == null || !inScope(scope, existing.record())) {
            return false;
        }
        return store.remove(memoryId.trim(), existing);
    }

    @Override
    public int purgeExpired(Instant now, int limit) {
        if (now == null || limit <= 0) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        for (PlatformMemoryVector vector : store.values()) {
            if (ids.size() >= limit) {
                break;
            }
            if (vector.record().expiredAt(now)) {
                ids.add(vector.record().memoryId());
            }
        }
        int deleted = 0;
        for (String id : ids) {
            if (store.remove(id) != null) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public List<PlatformMemoryRecord> listStaleEmbeddings(String currentModel, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return store.values().stream()
                .map(PlatformMemoryVector::record)
                .filter(record -> record.embeddingModel() == null
                        || record.embeddingModel().isBlank()
                        || !record.embeddingModel().equals(currentModel))
                .sorted(Comparator.comparing(PlatformMemoryRecord::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean updateEmbedding(String memoryId, float[] embedding, String embeddingModel) {
        if (memoryId == null || memoryId.isBlank()) {
            return false;
        }
        boolean[] updated = {false};
        store.computeIfPresent(memoryId.trim(), (id, existing) -> {
            PlatformMemoryRecord old = existing.record();
            PlatformMemoryRecord replaced = new PlatformMemoryRecord(
                    old.memoryId(), old.tenantKey(), old.userId(), old.sessionId(),
                    old.task(), old.answer(), old.createdAt(), old.expiresAt(), embeddingModel);
            updated[0] = true;
            return new PlatformMemoryVector(replaced, embedding == null ? new float[0] : embedding);
        });
        return updated[0];
    }

    private List<PlatformMemoryRecord> scoped(PlatformMemoryScope scope, Instant now) {
        return store.values().stream()
                .map(PlatformMemoryVector::record)
                .filter(record -> inScope(scope, record))
                .filter(record -> !record.expiredAt(now))
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
