package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台侧知识库绑定（Week 25）。
 *
 * <p>语义：把 enterprise 的一个知识库登记给某个租户。检索时按 {@code enterpriseUserId}
 * 执行（该本地用户拥有知识库），因此**不要求每个平台用户都有 SSO 映射**——
 * 绑定时由管理员以自己映射的企业身份导入即可。</p>
 *
 * @param bindingId        绑定 ID
 * @param tenantKey        归属租户
 * @param enterpriseUserId enterprise 本地用户 ID（知识库归属人）
 * @param knowledgeBaseId  enterprise 知识库 ID
 * @param name             知识库名称（导入时快照）
 * @param description      描述（导入时快照）
 * @param enabled          是否启用
 * @param createdBy        导入人（平台用户）
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record KnowledgeBinding(
        String bindingId,
        String tenantKey,
        long enterpriseUserId,
        long knowledgeBaseId,
        String name,
        String description,
        boolean enabled,
        long createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public KnowledgeBinding {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        tenantKey = tenantKey == null || tenantKey.isBlank() ? PlatformTenant.DEFAULT_TENANT_KEY : tenantKey.trim();
    }
}
