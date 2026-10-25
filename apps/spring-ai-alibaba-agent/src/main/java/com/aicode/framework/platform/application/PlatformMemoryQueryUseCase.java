package com.aicode.framework.platform.application;

import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 长期记忆查询（Week 28，包内协作类）：范围解析 + 时间/语义检索。
 *
 * <p>隔离规则与 Week 21 一致：scope 只能由用户记录推导；默认只查自己，仅 admin 可指定他人；
 * 向量化失败时检索返回空（不抛异常）。</p>
 */
final class PlatformMemoryQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformMemoryQueryUseCase.class);

    private final PlatformMemoryPort memoryPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final EmbeddingModelPort embeddingModelPort;
    private final PlatformMemoryProperties properties;

    PlatformMemoryQueryUseCase(
            PlatformMemoryPort memoryPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            EmbeddingModelPort embeddingModelPort,
            PlatformMemoryProperties properties
    ) {
        this.memoryPort = memoryPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.embeddingModelPort = embeddingModelPort;
        this.properties = properties;
    }

    List<PlatformMemoryHit> list(
            long callerUserId,
            Long targetUserId,
            String patientId,
            String query,
            int limit
    ) {
        return list(resolveScope(callerUserId, targetUserId), patientId, query, limit);
    }

    List<PlatformMemoryHit> list(
            PlatformMemoryScope scope,
            String patientId,
            String query,
            int limit
    ) {
        int effectiveLimit = normalizeLimit(limit);
        if (query != null && !query.isBlank()) {
            float[] queryVector = embedSafely(query);
            if (isEmpty(queryVector)) {
                return List.of();
            }
            return memoryPort.search(scope, queryVector, properties.resolvedCandidateLimit(),
                    effectiveLimit, properties.resolvedMinScore());
        }
        List<PlatformMemoryRecord> records = (patientId != null && !patientId.isBlank())
                ? memoryPort.listBySession(scope, patientId.trim(), effectiveLimit)
                : memoryPort.listRecent(scope, effectiveLimit);
        return records.stream().map(record -> new PlatformMemoryHit(record, 0.0)).toList();
    }

    PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
        final long effectiveUserId;
        if (targetUserId != null && targetUserId != callerUserId) {
            if (!permissionChecker.isAdmin(callerUserId)) {
                throw new PlatformAccessDeniedException("memory access denied for user: " + targetUserId);
            }
            effectiveUserId = targetUserId;
        } else {
            effectiveUserId = callerUserId;
        }
        PlatformUser user = userPort.findById(effectiveUserId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + effectiveUserId));
        return PlatformMemoryScope.of(user);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return properties.resolvedDefaultTopK();
        }
        return Math.min(limit, properties.resolvedMaxTopK());
    }

    private float[] embedSafely(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            return embeddingModelPort.embed(text);
        } catch (RuntimeException ex) {
            log.warn("[platform] memory embedding failed, search returns empty error={}", ex.getMessage());
            return new float[0];
        }
    }

    private boolean isEmpty(float[] vector) {
        return vector == null || vector.length == 0;
    }
}
