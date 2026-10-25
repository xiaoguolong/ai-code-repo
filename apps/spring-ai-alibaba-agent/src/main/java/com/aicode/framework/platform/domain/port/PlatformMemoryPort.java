package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;

import java.time.Instant;
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

    /**
     * 按范围删除单条记忆（Week 23）。
     *
     * @param scope    范围（必填）
     * @param memoryId 记忆 ID
     * @return true = 删除了一条；false = 不存在或不在该范围
     */
    boolean delete(PlatformMemoryScope scope, String memoryId);

    /**
     * 删除已过期记忆（Week 23）。
     *
     * <p>只删 {@code expires_at <= now} 的记录；{@code expires_at=null} 的永不过期记录不受影响。</p>
     *
     * @param now   当前时间
     * @param limit 单次最多删除条数
     * @return 实际删除条数
     */
    int purgeExpired(Instant now, int limit);

    /**
     * 取向量来源不是 {@code currentModel} 的记忆（Week 23，管理员运维）。
     *
     * <p><b>唯一不带 scope 的查询</b>：向量重建是跨用户的运维动作，调用方必须先做 admin 校验；
     * 普通读取路径仍必须使用带 scope 的方法。</p>
     *
     * @param currentModel 当前向量来源标识
     * @param limit        单批条数上限
     * @return 待重建记录（按创建时间升序），无数据返回空列表
     */
    List<PlatformMemoryRecord> listStaleEmbeddings(String currentModel, int limit);

    /**
     * 更新单条记忆的向量与来源标识（Week 23，管理员运维）。
     *
     * @param memoryId       记忆 ID
     * @param embedding      新向量
     * @param embeddingModel 新来源标识
     * @return true = 更新成功
     */
    boolean updateEmbedding(String memoryId, float[] embedding, String embeddingModel);
}
