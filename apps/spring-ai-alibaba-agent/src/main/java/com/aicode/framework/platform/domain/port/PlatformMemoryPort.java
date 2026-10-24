package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;

import java.util.List;

/**
 * 平台长期记忆端口（Week 21）。
 *
 * <p><b>会话隔离契约</b>：每个方法的第一个参数都是 {@link PlatformMemoryScope}，
 * 实现必须在 SQL 谓词（或等价过滤）里使用 {@code tenantKey + userId}；
 * 本接口刻意**不提供**「不带 scope 的查询」，避免调用方漏传导致跨租户泄露。</p>
 */
public interface PlatformMemoryPort {

    /**
     * 保存一条记忆。
     *
     * @param record    记忆记录（带 scope 字段）
     * @param embedding 向量，可为空数组（向量化失败时仍应落库）
     */
    void save(PlatformMemoryRecord record, float[] embedding);

    /**
     * 按时间倒序取范围内最近记忆（不做语义排序）。
     *
     * @param scope 范围（必填）
     * @param limit 条数上限
     * @return 记录列表，无数据返回空列表
     */
    List<PlatformMemoryRecord> listRecent(PlatformMemoryScope scope, int limit);

    /**
     * 取某个患者会话内的记忆（按时间倒序）。
     *
     * @param scope     范围（必填）
     * @param sessionId 会话标识（patientId），空则返回空列表
     * @param limit     条数上限
     * @return 记录列表，无数据返回空列表
     */
    List<PlatformMemoryRecord> listBySession(PlatformMemoryScope scope, String sessionId, int limit);

    /**
     * 语义检索：只在本范围内召回候选并排序。
     *
     * @param scope          范围（必填）
     * @param queryVector    查询向量；空向量返回空列表
     * @param candidateLimit 候选集上限（防止全表扫描）
     * @param topK           返回条数上限
     * @param minScore       最低余弦相似度
     * @return 按相似度降序的命中列表
     */
    List<PlatformMemoryHit> search(
            PlatformMemoryScope scope,
            float[] queryVector,
            int candidateLimit,
            int topK,
            double minScore);
}
