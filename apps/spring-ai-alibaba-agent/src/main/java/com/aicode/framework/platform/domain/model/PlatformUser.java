package com.aicode.framework.platform.domain.model;

/**
 * 平台用户。
 *
 * @param id           用户主键
 * @param username     登录名
 * @param passwordHash 密码哈希（明文永不落库）
 * @param roleKey      角色键
 * @param tenantKey    归属租户键（Week 21）；只由服务端读写，客户端无法指定
 */
public record PlatformUser(
        long id,
        String username,
        String passwordHash,
        String roleKey,
        String tenantKey
) {

    public PlatformUser {
        if (tenantKey == null || tenantKey.isBlank()) {
            tenantKey = PlatformTenant.DEFAULT_TENANT_KEY;
        }
        tenantKey = tenantKey.trim();
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
        this(id, username, passwordHash, roleKey, PlatformTenant.DEFAULT_TENANT_KEY);
    }
}
