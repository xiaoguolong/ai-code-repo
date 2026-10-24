package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.PlatformTenant;

/**
 * 平台登录结果。
 *
 * @param token     会话令牌
 * @param userId    用户主键
 * @param username  用户名
 * @param roleKey   角色键
 * @param tenantKey 租户键（Week 21）
 */
public record PlatformLoginOutcome(String token, long userId, String username, String roleKey, String tenantKey) {

    /**
     * 兼容 Week 13–20 的四参构造器（默认租户）。
     *
     * @param token    会话令牌
     * @param userId   用户主键
     * @param username 用户名
     * @param roleKey  角色键
     */
    public PlatformLoginOutcome(String token, long userId, String username, String roleKey) {
        this(token, userId, username, roleKey, PlatformTenant.DEFAULT_TENANT_KEY);
    }
}
