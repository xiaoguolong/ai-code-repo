package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 长期记忆生命周期用例（Week 23）：删除、过期清理、向量重建。
 *
 * <p>与 {@link PlatformMemoryUseCase} 分开的原因：读写是业务主链路，删除/清理/重建是
 * 低频运维动作，权限（admin）、批量上限与审计口径都不同，混在一个类里会超过 200 行且职责发散。</p>
 *
 * <p>隔离：删除走 {@link PlatformMemoryUseCase#resolveScope} 推导 scope；清理与重建必须先过
 * admin 校验。重建是唯一跨用户的查询，属显式运维例外（见 Port 注释）。</p>
 */
@Service
public class PlatformMemoryMaintenanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformMemoryMaintenanceUseCase.class);

    /** 清理/重建默认批量。 */
    public static final int DEFAULT_BATCH_LIMIT = 100;
    /** 清理/重建批量硬上限。 */
    public static final int MAX_BATCH_LIMIT = 500;

    private final PlatformMemoryUseCase memoryUseCase;
    private final PlatformMemoryPort memoryPort;
    private final PlatformPermissionChecker permissionChecker;
    private final EmbeddingModelPort embeddingModelPort;
    private final PlatformMemoryLifecycleProperties lifecycleProperties;
    private final MemoryEmbeddingIdentity embeddingIdentity;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public PlatformMemoryMaintenanceUseCase(
            PlatformMemoryUseCase memoryUseCase,
            PlatformMemoryPort memoryPort,
            PlatformPermissionChecker permissionChecker,
            EmbeddingModelPort embeddingModelPort,
            PlatformMemoryLifecycleProperties lifecycleProperties,
            MemoryEmbeddingIdentity embeddingIdentity,
            AuditLogPort auditLogPort,
            Clock clock
    ) {
        this.memoryUseCase = memoryUseCase;
        this.memoryPort = memoryPort;
        this.permissionChecker = permissionChecker;
        this.embeddingModelPort = embeddingModelPort;
        this.lifecycleProperties = lifecycleProperties;
        this.embeddingIdentity = embeddingIdentity;
        this.auditLogPort = auditLogPort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 按范围删除一条记忆。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可指定；null 表示自己）
     * @param memoryId     记忆 ID
     * @throws PlatformNotFoundException   不存在或不属于该范围（不泄露存在性）
     * @throws PlatformAccessDeniedException 非 admin 指定他人
     */
    public void delete(long callerUserId, Long targetUserId, String memoryId) {
        String normalizedId = requireMemoryId(memoryId);
        final PlatformMemoryScope scope;
        try {
            scope = memoryUseCase.resolveScope(callerUserId, targetUserId);
        } catch (PlatformAccessDeniedException ex) {
            audit(callerUserId, AuditAction.MEMORY_DELETE, "memory:" + normalizedId,
                    AuditResult.DENIED, "denied by scope");
            throw ex;
        }
        boolean deleted = memoryPort.delete(scope, normalizedId);
        if (!deleted) {
            audit(callerUserId, AuditAction.MEMORY_DELETE, "memory:" + normalizedId,
                    AuditResult.FAILURE, "not found in scope");
            throw new PlatformNotFoundException("memory not found: " + normalizedId);
        }
        audit(callerUserId, AuditAction.MEMORY_DELETE, "memory:" + normalizedId,
                AuditResult.SUCCESS, "tenantKey=" + scope.tenantKey() + " userId=" + scope.userId());
        log.info("[platform] memory deleted memoryId={} tenantKey={} userId={}",
                normalizedId, scope.tenantKey(), scope.userId());
    }

    /**
     * 手工清理过期记忆（仅 admin）。
     *
     * @param callerUserId 调用者
     * @param limit        单次最多删除条数
     * @return 实际删除条数
     * @throws PlatformAccessDeniedException 非 admin
     */
    public MemoryPurgeResult purgeExpired(long callerUserId, int limit) {
        requireAdmin(callerUserId);
        int effectiveLimit = normalizeBatchLimit(limit);
        int deleted = memoryPort.purgeExpired(clock.instant(), effectiveLimit);
        audit(callerUserId, AuditAction.MEMORY_PURGE, "memory:expired",
                AuditResult.SUCCESS, "deleted=" + deleted + " limit=" + effectiveLimit);
        log.info("[platform] memory purge expired deleted={} limit={} operator={}",
                deleted, effectiveLimit, callerUserId);
        return new MemoryPurgeResult(deleted, effectiveLimit);
    }

    /**
     * 定时清理过期记忆（无调用者，不写审计避免每天刷表）。
     *
     * @return 实际删除条数
     */
    public int purgeExpiredScheduled() {
        int limit = normalizeBatchLimit(lifecycleProperties.resolvedRebuildBatchLimit());
        int deleted = memoryPort.purgeExpired(clock.instant(), limit);
        if (deleted > 0) {
            log.info("[platform] scheduled memory purge deleted={} limit={}", deleted, limit);
        }
        return deleted;
    }

    /**
     * 重建缺失/旧模型向量（仅 admin）。
     *
     * @param callerUserId 调用者
     * @param limit        单批扫描条数
     * @return 扫描/重建/失败计数与当前模型标识
     * @throws PlatformAccessDeniedException 非 admin
     */
    public MemoryRebuildResult rebuildEmbeddings(long callerUserId, int limit) {
        requireAdmin(callerUserId);
        int effectiveLimit = normalizeBatchLimit(limit);
        String currentModel = embeddingIdentity == null ? "unknown" : embeddingIdentity.currentKey();
        List<PlatformMemoryRecord> candidates = memoryPort.listStaleEmbeddings(currentModel, effectiveLimit);
        int rebuilt = 0;
        int failed = 0;
        for (PlatformMemoryRecord record : candidates) {
            try {
                float[] vector = embeddingModelPort.embed(record.task());
                if (vector == null || vector.length == 0) {
                    failed++;
                    log.warn("[platform] memory rebuild produced empty vector memoryId={}", record.memoryId());
                    continue;
                }
                if (memoryPort.updateEmbedding(record.memoryId(), vector, currentModel)) {
                    rebuilt++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("[platform] memory rebuild failed memoryId={} error={}",
                        record.memoryId(), ex.getMessage());
            }
        }
        AuditResult result = failed == 0 ? AuditResult.SUCCESS : AuditResult.FAILURE;
        audit(callerUserId, AuditAction.MEMORY_REBUILD, "memory:embeddings", result,
                "scanned=" + candidates.size() + " rebuilt=" + rebuilt + " failed=" + failed);
        log.info("[platform] memory rebuild scanned={} rebuilt={} failed={} currentModel={} operator={}",
                candidates.size(), rebuilt, failed, currentModel, callerUserId);
        return new MemoryRebuildResult(candidates.size(), rebuilt, failed, currentModel);
    }

    private void requireAdmin(long callerUserId) {
        if (!permissionChecker.isAdmin(callerUserId)) {
            throw new PlatformAccessDeniedException("memory maintenance requires admin");
        }
    }

    private int normalizeBatchLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_BATCH_LIMIT;
        }
        return Math.min(limit, MAX_BATCH_LIMIT);
    }

    private String requireMemoryId(String memoryId) {
        String normalized = memoryId == null ? "" : memoryId.trim();
        if (normalized.isEmpty()) {
            throw new InvalidChatRequestException("memoryId must not be blank");
        }
        return normalized;
    }

    private void audit(
            long userId,
            AuditAction action,
            String resource,
            AuditResult result,
            String detail
    ) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, action, resource, result, TraceIds.current(), detail));
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action={} userId={} error={}",
                    action, userId, ex.getMessage());
        }
    }
}
