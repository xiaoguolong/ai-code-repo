package com.aicode.framework.platform.application;

import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 长期记忆写入（Week 28，包内协作类）：TTL + 向量来源 + 向量化失败降级。
 *
 * <p>向量化失败不阻塞写入（存空向量，检索时自然不命中）；TTL {@code <=0} 表示永不过期。</p>
 */
final class PlatformMemoryWriteUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformMemoryWriteUseCase.class);

    private final PlatformMemoryPort memoryPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final PlatformMemoryLifecycleProperties lifecycleProperties;
    private final Clock clock;
    private final MemoryEmbeddingIdentity embeddingIdentity;

    PlatformMemoryWriteUseCase(
            PlatformMemoryPort memoryPort,
            EmbeddingModelPort embeddingModelPort,
            PlatformMemoryLifecycleProperties lifecycleProperties,
            Clock clock,
            MemoryEmbeddingIdentity embeddingIdentity
    ) {
        this.memoryPort = memoryPort;
        this.embeddingModelPort = embeddingModelPort;
        this.lifecycleProperties = lifecycleProperties == null
                ? new PlatformMemoryLifecycleProperties(0, false, "", 0) : lifecycleProperties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.embeddingIdentity = embeddingIdentity;
    }

    PlatformMemoryRecord save(PlatformMemoryScope scope, String sessionId, String task, String answer) {
        String normalizedSession = sessionId == null ? "" : sessionId.trim();
        Instant createdAt = clock.instant();
        int ttlDays = lifecycleProperties.resolvedTtlDays();
        Instant expiresAt = ttlDays > 0 ? createdAt.plus(ttlDays, ChronoUnit.DAYS) : null;
        String embeddingModel = embeddingIdentity == null ? "unknown" : embeddingIdentity.currentKey();
        PlatformMemoryRecord record = new PlatformMemoryRecord(
                UUID.randomUUID().toString(),
                scope.tenantKey(),
                scope.userId(),
                normalizedSession,
                task == null ? "" : task,
                answer == null ? "" : answer,
                createdAt,
                expiresAt,
                embeddingModel);
        memoryPort.save(record, embedSafely(task));
        log.info("[platform] memory saved memoryId={} tenantKey={} userId={} sessionId={} chars={} expiresAt={} embeddingModel={}",
                record.memoryId(), record.tenantKey(), record.userId(), record.sessionId(),
                record.answer().length(), expiresAt, embeddingModel);
        return record;
    }

    private float[] embedSafely(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            return embeddingModelPort.embed(text);
        } catch (RuntimeException ex) {
            log.warn("[platform] memory embedding failed, persist without vector error={}", ex.getMessage());
            return new float[0];
        }
    }
}
