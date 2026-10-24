package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformTenant;

import java.time.Instant;

/**
 * 租户响应（Week 21）。
 *
 * @param tenantKey   租户键
 * @param displayName 展示名
 * @param status      状态（ACTIVE / SUSPENDED）
 * @param createdAt   创建时间
 */
public record PlatformTenantResponse(String tenantKey, String displayName, String status, Instant createdAt) {

    /**
     * 由领域模型转换。
     *
     * @param tenant 租户
     * @return 响应体
     */
    public static PlatformTenantResponse from(PlatformTenant tenant) {
        return new PlatformTenantResponse(tenant.tenantKey(), tenant.displayName(),
                tenant.status().name(), tenant.createdAt());
    }
}
