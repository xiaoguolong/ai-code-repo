package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;

import java.util.List;
import java.util.Optional;

/**
 * 知识库绑定端口（Week 25）。
 *
 * <p>所有查询都必须带 {@code tenant_key}：绑定是租户级资源，不接受跨租户读取。</p>
 */
public interface KnowledgeBindingPort {

    /**
     * 保存（按 bindingId 覆盖写）。
     *
     * @param binding 绑定
     */
    void save(KnowledgeBinding binding);

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
     * 删除绑定（带租户谓词）。
     *
     * @param tenantKey 租户
     * @param bindingId 绑定 ID
     * @return true = 删除了一条
     */
    boolean delete(String tenantKey, String bindingId);
}
