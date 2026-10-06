package com.aicode.gateway.domain.model;

/**
 * 平台登录结果（网关侧视图）。
 *
 * @param platformToken 平台签发的 token（内部凭据，不对外返回）
 * @param userId        用户 ID
 * @param username      登录名
 * @param roleKey       角色标识
 */
public record PlatformLogin(String platformToken, long userId, String username, String roleKey) {
}
