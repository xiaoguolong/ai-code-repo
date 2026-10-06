package com.aicode.gateway.infrastructure.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 平台登录请求体的 Feign 侧形态（Week 19）。
 *
 * @param username 登录名
 * @param password 密码（只在内存中传递，禁止日志）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeignLoginRequest(String username, String password) {
}
