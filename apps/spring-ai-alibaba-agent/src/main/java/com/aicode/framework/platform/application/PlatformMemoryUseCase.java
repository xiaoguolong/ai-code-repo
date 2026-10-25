package com.aicode.framework.platform.application;

import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformMemoryPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryLifecycleProperties;
import com.aicode.framework.platform.infrastructure.config.PlatformMemoryProperties;
import com.aicode.framework.platform.infrastructure.embedding.MemoryEmbeddingIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 长期记忆用例（Week 21；Week 28 拆为门面）。
 *
 * <p>内部协作类：{@link PlatformMemoryWriteUseCase}（保存 + TTL + 向量来源）、
 * {@link PlatformMemoryQueryUseCase}（范围解析 + 时间/语义检索）。本类保留原构造器与
 * save/list/resolveScope 公共方法，控制器、执行/审批副作用与既有测试不改。</p>
 */
@Service
public class PlatformMemoryUseCase {

    private final PlatformMemoryWriteUseCase writeUseCase;
    private final PlatformMemoryQueryUseCase queryUseCase;

    /**
     * Week 21 兼容构造器：不启用 TTL、不标记向量来源（供既有单测直接 new）。
     *
     * @param memoryPort         记忆端口
     * @param userPort           用户端口
     * @param permissionChecker  权限校验
     * @param embeddingModelPort 向量化端口
     * @param properties         记忆检索配置
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
     * @param memoryPort          记忆端口
     * @param userPort            用户端口
     * @param permissionChecker   权限校验
     * @param embeddingModelPort  向量化端口
     * @param properties          记忆检索配置
     * @param lifecycleProperties 生命周期配置（TTL）
     * @param clock               UTC 时钟（单测注入固定时钟）
     * @param embeddingIdentity   当前向量来源标识（可为 null，测试兼容）
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
        this.writeUseCase = new PlatformMemoryWriteUseCase(
                memoryPort, embeddingModelPort, lifecycleProperties, clock, embeddingIdentity);
        this.queryUseCase = new PlatformMemoryQueryUseCase(
                memoryPort, userPort, permissionChecker, embeddingModelPort, properties);
    }

    /**
     * 保存一条长期记忆（TTL + 向量来源；向量化失败降级为空向量）。
     *
     * @param scope     访问范围（非空，由服务端推导）
     * @param sessionId 会话标识（patientId）
     * @param task      任务原文
     * @param answer    结论/报告
     * @return 已保存的记录（含 memoryId 与时间）
     */
    public PlatformMemoryRecord save(
            PlatformMemoryScope scope, String sessionId, String task, String answer) {
        return writeUseCase.save(scope, sessionId, task, answer);
    }

    /**
     * 查询长期记忆。
     *
     * @param callerUserId 调用者用户 ID
     * @param targetUserId 目标用户（仅 admin 可传；null 表示查自己）
     * @param patientId    按患者会话过滤，空白视为不过滤
     * @param query        语义检索文本，空白则按时间倒序
     * @param limit        条数上限，&lt;=0 用默认值
     * @return 命中列表
     */
    public List<PlatformMemoryHit> list(
            long callerUserId,
            Long targetUserId,
            String patientId,
            String query,
            int limit
    ) {
        return queryUseCase.list(callerUserId, targetUserId, patientId, query, limit);
    }

    /**
     * 已解析范围后查询（控制器先 resolveScope 回显，再调本方法）。
     *
     * @param scope     访问范围
     * @param patientId 按患者会话过滤
     * @param query     语义检索文本
     * @param limit     条数上限
     * @return 命中列表
     */
    public List<PlatformMemoryHit> list(
            PlatformMemoryScope scope,
            String patientId,
            String query,
            int limit
    ) {
        return queryUseCase.list(scope, patientId, query, limit);
    }

    /**
     * 解析可访问范围（供控制器回显 tenantKey / userId）。
     *
     * @param callerUserId 调用者
     * @param targetUserId 目标用户（仅 admin 可传）
     * @return 范围
     */
    public PlatformMemoryScope resolveScope(long callerUserId, Long targetUserId) {
        return queryUseCase.resolveScope(callerUserId, targetUserId);
    }
}
