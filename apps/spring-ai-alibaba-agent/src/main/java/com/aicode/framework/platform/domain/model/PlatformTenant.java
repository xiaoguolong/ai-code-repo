package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台租户（Week 21，医疗 SaaS 的第一道边界）。
 *
 * @param tenantKey   租户键，如 {@code default} / {@code hospital-b}，全局唯一
 * @param displayName 展示名
 * @param status      状态，停用后不允许登录
 * @param createdAt   创建时间
 */
public record PlatformTenant(String tenantKey, String displayName, TenantStatus status, Instant createdAt) {

    /** 默认租户键：迁移后的既有数据全部落在这里。 */
    public static final String DEFAULT_TENANT_KEY = "default";

    public PlatformTenant {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalArgumentException("tenantKey must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        tenantKey = tenantKey.trim();
        displayName = displayName.trim();
        if (status == null) {
            status = TenantStatus.ACTIVE;
        }
    }

    /**
     * @return 是否可用（只有 ACTIVE 允许登录与使用）
     */
    public boolean active() {
        return status == TenantStatus.ACTIVE;
    }
}
