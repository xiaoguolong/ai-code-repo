package com.aicode.framework.platform.application;

/**
 * 当前用户视图（Week 21，`/auth/me` 用）。
 *
 * @param userId     用户主键
 * @param username   用户名
 * @param roleKey    角色键
 * @param tenantKey  租户键
 * @param tenantName 租户展示名；租户缺失时回退为租户键
 */
public record PlatformMeView(long userId, String username, String roleKey, String tenantKey, String tenantName) {
}
