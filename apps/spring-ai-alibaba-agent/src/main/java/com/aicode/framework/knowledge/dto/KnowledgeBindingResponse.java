package com.aicode.framework.knowledge.dto;

import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;

import java.time.Instant;

/**
 * 知识库绑定响应（Week 25，Week 27 加同步状态）。
 *
 * @param bindingId       绑定 ID
 * @param knowledgeBaseId 企业知识库 ID
 * @param name            名称
 * @param description     描述
 * @param enabled         是否启用
 * @param syncStatus      与企业侧的同步状态（UNKNOWN / ACTIVE / MISSING）
 * @param lastSyncedAt    最近一次成功对账时间；从未对账为 null
 * @param createdAt       导入时间
 */
public record KnowledgeBindingResponse(
        String bindingId,
        long knowledgeBaseId,
        String name,
        String description,
        boolean enabled,
        KnowledgeBindingSyncStatus syncStatus,
        Instant lastSyncedAt,
        Instant createdAt
) {

    /**
     * 由领域模型转换。
     *
     * @param binding 绑定
     * @return 响应体
     */
    public static KnowledgeBindingResponse from(KnowledgeBinding binding) {
        return new KnowledgeBindingResponse(
                binding.bindingId(), binding.knowledgeBaseId(), binding.name(),
                binding.description(), binding.enabled(), binding.syncStatus(),
                binding.lastSyncedAt(), binding.createdAt());
    }
}
