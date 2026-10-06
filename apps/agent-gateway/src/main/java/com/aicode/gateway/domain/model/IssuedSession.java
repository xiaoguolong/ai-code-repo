package com.aicode.gateway.domain.model;

/**
 * 已签发的网关联会话（返回给客户端的那一份）。
 *
 * @param sessionToken      网关联会话 token（对外凭据）
 * @param userId            用户 ID
 * @param username          登录名
 * @param roleKey           角色标识
 * @param expiresInSeconds  会话有效期（秒）
 */
public record IssuedSession(
        String sessionToken,
        long userId,
        String username,
        String roleKey,
        long expiresInSeconds
) {
}
