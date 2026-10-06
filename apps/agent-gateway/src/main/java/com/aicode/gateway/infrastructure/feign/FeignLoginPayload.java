package com.aicode.gateway.infrastructure.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 平台登录响应 data 的 Feign 侧形态（Week 19）。
 *
 * @param token    平台 token（内部凭据，不对外返回）
 * @param userId   用户 ID
 * @param username 登录名
 * @param roleKey  角色标识
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeignLoginPayload(String token, long userId, String username, String roleKey) {
}
