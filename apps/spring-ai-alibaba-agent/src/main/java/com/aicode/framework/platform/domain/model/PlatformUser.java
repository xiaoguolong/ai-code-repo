package com.aicode.framework.platform.domain.model;

import java.time.Instant;

/**
 * 平台用户。
 *
 * @param id           用户主键
 * @param username     登录名
 * @param passwordHash 密码哈希（明文永不落库）
 * @param roleKey      角色键
 * @param tenantKey    归属租户键（Week 21）；只由服务端读写，客户端无法指定
 * @param disabledAt   停用时间（Week 28）；null = 正常。停用不删行，历史数据保留
 */
public record PlatformUser(
        long id,
        String username,
        String passwordHash,
        String roleKey,
        String tenantKey,
        Instant disabledAt
) {

    public PlatformUser {
        if (tenantKey == null || tenantKey.isBlank()) {
            tenantKey = PlatformTenant.DEFAULT_TENANT_KEY;
        }
        tenantKey = tenantKey.trim();
    }

    /**
     * 兼容 Week 21–27 的五参构造器（默认正常状态）。
     *
     * @param id           用户主键
     * @param username     登录名
     * @param passwordHash 密码哈希
     * @param roleKey      角色键
     * @param tenantKey    租户键
     */
    public PlatformUser(long id, String username, String passwordHash, String roleKey, String tenantKey) {
        this(id, username, passwordHash, roleKey, tenantKey, null);
    }

    /**
     * 兼容 Week 13–20 的四参构造器（默认落在 {@code default} 租户）。
     *
     * @param id           用户主键
     * @param username     登录名
     * @param passwordHash 密码哈希
     * @param roleKey      角色键
     */
    public PlatformUser(long id, String username, String passwordHash, String roleKey) {
        this(id, username, passwordHash, roleKey, PlatformTenant.DEFAULT_TENANT_KEY, null);
    }

    /**
     * @return true = 已停用（登录/verify 必须拒绝）
     */
    public boolean disabled() {
        return disabledAt != null;
    }

    /**
     * 生成停用/恢复后的副本；{@code disabledAt=null} 表示恢复。
     *
     * @param newDisabledAt 新的停用时间；null = 恢复
     * @return 新用户记录
     */
    public PlatformUser withDisabledAt(Instant newDisabledAt) {
        return new PlatformUser(id, username, passwordHash, roleKey, tenantKey, newDisabledAt);
    }
}
