package com.aicode.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 网关登录请求（Week 19）。
 *
 * @param username 登录名
 * @param password 密码（只在内存中传递，禁止日志与响应回显）
 */
public record GatewayLoginRequest(
        @NotBlank(message = "username must not be blank") String username,
        @NotBlank(message = "password must not be blank") String password
) {
}
