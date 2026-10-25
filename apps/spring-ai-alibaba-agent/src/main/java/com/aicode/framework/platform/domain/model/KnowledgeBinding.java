package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台侧知识库绑定（Week 25，Week 27 加同步状态）。
 *
 * <p>语义：把 enterprise 的一个知识库登记给某个租户。检索时按 {@code enterpriseUserId}
 * 执行（该本地用户拥有知识库），因此**不要求每个平台用户都有 SSO 映射**——
 * 绑定时由管理员以自己映射的企业身份导入即可。</p>
 *
 * <p>{@code syncStatus}/{@code lastSyncedAt} 由对账任务维护；它们只描述企业侧的存在性，
 * 不参与授权判断。对账失败时保持原值（见 {@link KnowledgeBindingSyncStatus}）。</p>
 *
 * @param bindingId        绑定 ID
 * @param tenantKey        归属租户
 * @param enterpriseUserId enterprise 本地用户 ID（知识库归属人）
 * @param knowledgeBaseId  enterprise 知识库 ID
 * @param name             知识库名称（最近一次对账后的值）
 * @param description      描述（最近一次对账后的值）
 * @param enabled          是否启用（租户维度的检索开关）
 * @param createdBy        导入人（平台用户）
 * @param createdAt        创建时间
 * @param updatedAt        元数据更新时间（对账只刷新同步状态时不改它）
 * @param syncStatus       与企业侧的同步状态
 * @param lastSyncedAt     最近一次成功对账时间；从未对账为 null
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
        Instant updatedAt,
        KnowledgeBindingSyncStatus syncStatus,
        Instant lastSyncedAt
) {

    public KnowledgeBinding {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        tenantKey = tenantKey == null || tenantKey.isBlank() ? PlatformTenant.DEFAULT_TENANT_KEY : tenantKey.trim();
        syncStatus = syncStatus == null ? KnowledgeBindingSyncStatus.UNKNOWN : syncStatus;
    }

    /**
     * 兼容 Week 25 调用方（导入/测试）：同步状态默认为 {@link KnowledgeBindingSyncStatus#UNKNOWN}。
     *
     * @param bindingId        绑定 ID
     * @param tenantKey        租户
     * @param enterpriseUserId 企业归属人
     * @param knowledgeBaseId  知识库 ID
     * @param name             名称
     * @param description      描述
     * @param enabled          是否启用
     * @param createdBy        导入人
     * @param createdAt        创建时间
     * @param updatedAt        更新时间
     */
    public KnowledgeBinding(
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
        this(bindingId, tenantKey, enterpriseUserId, knowledgeBaseId, name, description, enabled,
                createdBy, createdAt, updatedAt, KnowledgeBindingSyncStatus.UNKNOWN, null);
    }

    /**
     * 企业侧仍存在：同步名称/描述并把状态置为 ACTIVE。
     *
     * <p>只在元数据确有变化时由用例调用（避免每天白写一行）；状态迁移 + 时间戳刷新
     * 走端口上的轻量方法，不改 {@code updatedAt}。</p>
     *
     * @param newName        企业侧最新名称
     * @param newDescription 企业侧最新描述
     * @param syncedAt       对账时间
     * @return 更新后的绑定
     */
    public KnowledgeBinding synced(String newName, String newDescription, Instant syncedAt) {
        return new KnowledgeBinding(
                bindingId, tenantKey, enterpriseUserId, knowledgeBaseId,
                newName, newDescription, enabled, createdBy, createdAt, syncedAt,
                KnowledgeBindingSyncStatus.ACTIVE, syncedAt);
    }

    /**
     * 企业侧已不存在：仅用于调用方需要整体替换时的语义表达。
     *
     * <p>生产实现用 {@code KnowledgeBindingPort#updateSyncState} 做轻量更新；
     * 本方法保留给内存实现/测试构造。</p>
     *
     * @param syncedAt 对账时间
     * @return MISSING 状态的绑定
     */
    public KnowledgeBinding markMissing(Instant syncedAt) {
        return new KnowledgeBinding(
                bindingId, tenantKey, enterpriseUserId, knowledgeBaseId,
                name, description, enabled, createdBy, createdAt, updatedAt,
                KnowledgeBindingSyncStatus.MISSING, syncedAt);
    }

    /**
     * @return true = 企业侧已确认删除（检索必须拒绝）
     */
    public boolean missing() {
        return syncStatus == KnowledgeBindingSyncStatus.MISSING;
    }

    /**
     * @return true = 允许检索（启用且未被对账判定删除；UNKNOWN 视为可检索）
     */
    public boolean retrievable() {
        return enabled && !missing();
    }
}
