package com.aicode.gateway.dto;

import com.aicode.gateway.domain.model.IssuedSession;

/**
 * 网关登录响应（Week 19）。
 *
 * <p><b>只回网关联会话 token</b>：平台 token 是网关与平台之间的内部凭据，
 * 回给客户端等于把内部凭据（以及它的权限面）暴露出去，也违反「网关是唯一信任边界」的设计。</p>
 *
 * @param sessionToken     网关联会话 token（对外凭据）
 * @param tokenName        客户端应使用的头名（默认 {@code satoken}，也支持 {@code Authorization: Bearer}）
 * @param userId           用户 ID
 * @param username         登录名
 * @param roleKey          角色标识（仅展示，权限裁决在平台）
 * @param expiresInSeconds 会话有效期（秒）
 */
public record GatewayLoginResponse(
        String sessionToken,
        String tokenName,
        long userId,
        String username,
        String roleKey,
        long expiresInSeconds
) {

    /**
     * 领域模型 → 响应体。
     *
     * @param session   已签发会话
     * @param tokenName 客户端使用的头名
     * @return 响应体
     */
    public static GatewayLoginResponse from(IssuedSession session, String tokenName) {
        return new GatewayLoginResponse(
                session.sessionToken(), tokenName, session.userId(), session.username(),
                session.roleKey(), session.expiresInSeconds());
    }
}
