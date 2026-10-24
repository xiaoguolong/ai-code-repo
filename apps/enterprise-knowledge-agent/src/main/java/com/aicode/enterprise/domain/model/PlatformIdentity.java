package com.aicode.enterprise.domain.model;

/**
 * 平台身份（Week 21 SSO）。
 *
 * <p>只描述「平台认定你是谁」，**不含平台 token**：enterprise 用自己的会话，
 * 平台身份仅用于建链与本地开户。</p>
 *
 * @param userId    平台用户主键
 * @param username  用户名
 * @param roleKey   平台角色键（仅记录，enterprise 授权仍走自己的规则）
 * @param tenantKey 租户键
 */
public record PlatformIdentity(long userId, String username, String roleKey, String tenantKey) {

    public PlatformIdentity {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }
}
