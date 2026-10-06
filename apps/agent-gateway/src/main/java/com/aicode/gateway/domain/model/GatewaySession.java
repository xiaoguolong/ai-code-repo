package com.aicode.gateway.domain.model;

/**
 * 网关联会话（Week 19）。
 *
 * <p>网关不保存用户库，也不复制 RBAC 规则：登录由平台裁决，网关只保存
 * 「网关联会话 token → 平台 token + 调用者身份摘要」的映射，供后续请求换发
 * {@code Authorization} 头使用。</p>
 *
 * <p><b>安全约定</b>：{@link #platformToken()} 是内部凭据，只用于网关→平台的 Feign 调用，
 * <b>绝不出现在任何对外响应中</b>（自检接口也只回布尔值）。</p>
 *
 * @param platformToken 平台签发的 token
 * @param userId        平台用户 ID
 * @param username      登录名（用于日志与自检展示）
 * @param roleKey       角色标识（仅展示用，权限裁决仍在平台）
 * @param expiresAtEpochSecond 会话绝对过期时间（Unix 秒）
 */
public record GatewaySession(
        String platformToken,
        long userId,
        String username,
        String roleKey,
        long expiresAtEpochSecond
) {

    /**
     * 会话是否已过期。
     *
     * @param nowEpochSecond 当前 Unix 秒
     * @return 已过期返回 true
     */
    public boolean expiredAt(long nowEpochSecond) {
        return expiresAtEpochSecond > 0 && nowEpochSecond >= expiresAtEpochSecond;
    }
}
