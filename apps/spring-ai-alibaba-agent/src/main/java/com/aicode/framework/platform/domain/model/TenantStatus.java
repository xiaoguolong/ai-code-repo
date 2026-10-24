package com.aicode.framework.platform.domain.model;

import java.util.Locale;

/**
 * 租户状态（Week 21）。
 *
 * <p>只允许两态：{@link #ACTIVE} 可用、{@link #SUSPENDED} 停用。停用语义是「不允许新登录」，
 * 对外仍返回 401 且不解释原因（避免暴露租户状态）。</p>
 */
public enum TenantStatus {

    /** 正常可用。 */
    ACTIVE,

    /** 已停用：不允许登录。 */
    SUSPENDED;

    /**
     * 从数据库字符串解析，大小写不敏感。
     *
     * @param raw 原始值，可为 null/空
     * @return 对应枚举；未知值按 {@link #ACTIVE} 处理（迁移前的老数据没有该列值）
     */
    public static TenantStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (TenantStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return ACTIVE;
    }
}
