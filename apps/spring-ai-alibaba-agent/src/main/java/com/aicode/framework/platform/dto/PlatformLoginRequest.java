package com.aicode.framework.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 平台登录请求。
 */
public record PlatformLoginRequest(
        @NotBlank(message = "username must not be blank") String username,
        @NotBlank(message = "password must not be blank") String password
) {
}
