package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库绑定端口（Week 25，Week 27 加对账支持）。
 *
 * <p>所有租户维度查询都必须带 {@code tenant_key}：绑定是租户级资源，不接受跨租户读取。
 * 仅 {@link #listAll()} 与 {@link #countBySyncStatus} 是运维/调度用途，不向普通请求暴露。</p>
 */
public interface KnowledgeBindingPort {

    /**
     * 保存（按 bindingId 覆盖写，含元数据与同步状态）。
     *
     * @param binding 绑定
     */
    void save(KnowledgeBinding binding);

    /**
     * 只更新同步状态与最近对账时间，<b>不改</b>名称/描述/{@code updatedAt}。
     *
     * <p>对账时元数据没变也要留下「今天检查过」的证据，但不应把 {@code updatedAt}
     * 变成每日一次的心跳。由对账用例在元数据无变化 / 标记 MISSING 时调用。</p>
     *
     * @param bindingId  绑定 ID
     * @param syncStatus 新状态
     * @param syncedAt   对账时间
     * @return true = 更新了一条
     */
    boolean updateSyncState(String bindingId, KnowledgeBindingSyncStatus syncStatus, Instant syncedAt);

    /**
     * 按租户 + 知识库查绑定。
     *
     * @param tenantKey       租户
     * @param knowledgeBaseId 知识库 ID
     * @return 绑定；未绑定为空
     */
    Optional<KnowledgeBinding> findByTenantAndKnowledgeBase(String tenantKey, long knowledgeBaseId);

    /**
     * 按 ID 查询（不做租户校验，调用方必须自行校验）。
     *
     * @param bindingId 绑定 ID
     * @return 绑定；不存在为空
     */
    Optional<KnowledgeBinding> findById(String bindingId);

    /**
     * 列出某租户的绑定。
     *
     * @param tenantKey 租户
     * @return 按创建时间倒序的绑定，无数据返回空列表
     */
    List<KnowledgeBinding> listByTenant(String tenantKey);

    /**
     * 列出全部租户的绑定（仅调度对账使用）。
     *
     * @return 按租户 + 创建时间倒序的绑定，无数据返回空列表
     */
    List<KnowledgeBinding> listAll();

    /**
     * 统计某同步状态的绑定数（对账 Gauge 用，全局口径）。
     *
     * @param syncStatus 同步状态
     * @return 条数，无数据返回 0
     */
    long countBySyncStatus(KnowledgeBindingSyncStatus syncStatus);

    /**
     * 删除绑定（带租户谓词）。
     *
     * @param tenantKey 租户
     * @param bindingId 绑定 ID
     * @return true = 删除了一条
     */
    boolean delete(String tenantKey, String bindingId);
}
