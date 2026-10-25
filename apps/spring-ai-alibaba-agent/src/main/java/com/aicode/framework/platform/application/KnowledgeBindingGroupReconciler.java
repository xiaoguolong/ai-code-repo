package com.aicode.framework.platform.application;

import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个对账分组的差异应用（Week 27，包内协作类）。
 *
 * <p>纯决策 + 端口写入：企业侧存在且元数据变化 → 保存；未变化 → 只刷新同步列（不动
 * {@code updatedAt}）；企业侧缺失 → 标 {@code MISSING}（<b>不删行</b>）。</p>
 *
 * <p>分组调用失败由 {@link KnowledgeBindingReconcileUseCase} 捕获并计 {@code failed}，
 * 本类只处理已经拿到的企业清单。</p>
 */
final class KnowledgeBindingGroupReconciler {

    private final KnowledgeBindingPort bindingPort;

    KnowledgeBindingGroupReconciler(KnowledgeBindingPort bindingPort) {
        this.bindingPort = bindingPort;
    }

    /**
     * 应用一组绑定的对账结果。
     *
     * @param group                 同租户 + 同企业归属人的绑定
     * @param catalog               企业侧清单
     * @param boundKnowledgeBaseIds 本租户已绑定的知识库 ID（用于统计 unbound）
     * @param now                   对账时间
     * @return 该组的计数
     */
    GroupOutcome apply(
            List<KnowledgeBinding> group,
            KnowledgeBaseCatalog catalog,
            Set<Long> boundKnowledgeBaseIds,
            Instant now
    ) {
        Map<Long, KnowledgeBaseCatalogEntry> byId = byId(catalog);
        int unbound = countUnbound(catalog, boundKnowledgeBaseIds);
        int updated = 0;
        int missing = 0;
        int unchanged = 0;
        for (KnowledgeBinding binding : group) {
            KnowledgeBaseCatalogEntry found = byId.get(binding.knowledgeBaseId());
            if (found == null) {
                bindingPort.updateSyncState(
                        binding.bindingId(), KnowledgeBindingSyncStatus.MISSING, now);
                missing++;
            } else if (metadataChanged(binding, found)) {
                bindingPort.save(binding.synced(found.name(), found.description(), now));
                updated++;
            } else {
                bindingPort.updateSyncState(
                        binding.bindingId(), KnowledgeBindingSyncStatus.ACTIVE, now);
                unchanged++;
            }
        }
        return new GroupOutcome(updated, missing, unchanged, unbound);
    }

    private Map<Long, KnowledgeBaseCatalogEntry> byId(KnowledgeBaseCatalog catalog) {
        Map<Long, KnowledgeBaseCatalogEntry> byId = new HashMap<>();
        for (KnowledgeBaseCatalogEntry entry : catalog.bases()) {
            byId.put(entry.knowledgeBaseId(), entry);
        }
        return byId;
    }

    private int countUnbound(KnowledgeBaseCatalog catalog, Set<Long> boundKnowledgeBaseIds) {
        int unbound = 0;
        for (KnowledgeBaseCatalogEntry entry : catalog.bases()) {
            if (!boundKnowledgeBaseIds.contains(entry.knowledgeBaseId())) {
                unbound++;
            }
        }
        return unbound;
    }

    private boolean metadataChanged(KnowledgeBinding binding, KnowledgeBaseCatalogEntry entry) {
        return binding.syncStatus() != KnowledgeBindingSyncStatus.ACTIVE
                || !binding.name().equals(entry.name())
                || !binding.description().equals(entry.description());
    }

    /**
     * 单组对账计数。
     *
     * @param updated   更新条数
     * @param missing   标记 MISSING 条数
     * @param unchanged 未变化条数
     * @param unbound   企业侧存在但未绑定条数
     */
    record GroupOutcome(int updated, int missing, int unchanged, int unbound) {
    }
}
