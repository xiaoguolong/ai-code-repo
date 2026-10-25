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
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 长期记忆用例（Week 21）：写入、按范围查询、语义检索。
 *
 * <p>隔离规则（写死并单测）：</p>
 * <ul>
 *   <li>scope 只能由「用户记录」推导，请求里没有也不接受 tenantKey；</li>
 *   <li>默认只查自己；仅 admin 可以指定 {@code userId} 查他人，非 admin 直接 403；</li>
 *   <li>向量化失败不阻塞写入（存空向量，检索时自然不命中），也不抛异常给业务。</li>
 * </ul>
 */
@Service
public class PlatformMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformMemoryUseCase.class);

    private final PlatformMemoryPort memoryPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final EmbeddingModelPort embeddingModelPort;
    private final PlatformMemoryProperties properties;
    private final PlatformMemoryLifecycleProperties lifecycleProperties;
    private final Clock clock;
    private final MemoryEmbeddingIdentity embeddingIdentity;

    /**
     * Week 21 兼容构造器：不启用 TTL、不标记向量来源（供既有单测直接 new）。
     *
     * @param memoryPort        记忆端口
     * @param userPort          用户端口
     * @param permissionChecker 权限校验
     * @param embeddingModelPort 向量化端口
     * @param properties        记忆检索配置
     */
    public PlatformMemoryUseCase(
            PlatformMemoryPort memoryPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            EmbeddingModelPort embeddingModelPort,
            PlatformMemoryProperties properties
    ) {
        this(memoryPort, userPort, permissionChecker, embeddingModelPort, properties,
                new PlatformMemoryLifecycleProperties(0, false, "", 0),
                Clock.systemUTC(), null);
    }

    /**
     * Spring 注入构造器（Week 23）：TTL 与向量来源标识需要配置、时钟与 embedding 身份。
     *
     * @param memoryPort         记忆端口
     * @param userPort           用户端口
     * @param permissionChecker  权限校验
     * @param embeddingModelPort 向量化端口
     * @param properties         记忆检索配置
     * @param lifecycleProperties 生命周期配置（TTL）
     * @param clock              UTC 时钟（单测注入固定时钟）
     * @param embeddingIdentity  当前向量来源标识（可为 null，测试兼容）
     */
    @Autowired
    public PlatformMemoryUseCase(
            PlatformMemoryPort memoryPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            EmbeddingModelPort embeddingModelPort,
            PlatformMemoryProperties properties,
            PlatformMemoryLifecycleProperties lifecycleProperties,
            Clock clock,
            MemoryEmbeddingIdentity embeddingIdentity
    ) {
        this.memoryPort = memoryPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.embeddingModelPort = embeddingModelPort;
        this.properties = properties;
        this.lifecycleProperties = lifecycleProperties == null
                ? new PlatformMemoryLifecycleProperties(0, false, "", 0) : lifecycleProperties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.embeddingIdentity = embeddingIdentity;
    }

    /**
     * 保存一条长期记忆。
     *
     * <p>Week 23 起同时写入 {@code expires_at}（TTL，{@code <=0} 为永不过期）与
     * {@code embedding_model}（换模型后可识别旧向量）。</p>
     *
     * @param scope    访问范围（非空，由服务端推导）
     * @param sessionId 会话标识（patientId）
     * @param task      任务原文
     * @param answer    结论/报告
     * @return 已保存的记录（含 memoryId 与时间）
     */
    public PlatformMemoryRecord save(PlatformMemoryScope scope, String sessionId, String task, String answer) {
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

    /**
     * 查询长期记忆。
     *
     * @param callerUserId 调用者用户 ID
     * @param targetUserId 目标用户（仅 admin 可传；null 表示查自己）
     * @param patientId    按患者会话过滤，空白视为不过滤
     * @param query        语义检索文本，空白则按时间倒序
     * @param limit        条数上限，&lt;=0 用默认值，超上限被截断
     * @return 命中列表；按时间列表时 score 为 0
     * @throws PlatformAccessDeniedException 非 admin 指定 userId
     * @throws PlatformNotFoundException     目标用户不存在
     */
    public List<PlatformMemoryHit> list(
            long callerUserId,
            Long targetUserId,
            String patientId,
            String query,
            int limit
    ) {
        return list(resolveScope(callerUserId, targetUserId), patientId, query, limit);
    }

    /**
     * 已解析范围后查询（控制器先 {@link #resolveScope} 回显 scope，再调本方法，避免重复解析）。
     *
     * @param scope     访问范围
     * @param patientId 按患者会话过滤，空白视为不过滤
     * @param query     语义检索文本，空白则按时间倒序
     * @param limit     条数上限
     * @return 命中列表；按时间列表时 score 为 0
     */
    public List<PlatformMemoryHit> list(
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

    /**
     * 解析可访问范围（供控制器回显 tenantKey / userId）。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可传）
     * @return 范围
     * @throws PlatformAccessDeniedException 非 admin 指定他人
     * @throws PlatformNotFoundException     用户不存在
     */
    public PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
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
            log.warn("[platform] memory embedding failed, persist without vector error={}", ex.getMessage());
            return new float[0];
        }
    }

    private boolean isEmpty(float[] vector) {
        return vector == null || vector.length == 0;
    }
}
