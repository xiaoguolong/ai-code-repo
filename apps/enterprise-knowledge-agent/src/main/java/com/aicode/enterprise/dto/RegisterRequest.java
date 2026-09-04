package com.aicode.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。密码只走服务端哈希，不回显。
 */
public record RegisterRequest(
        @NotBlank(message = "username 不能为空")
        @Size(max = 64, message = "username 长度不能超过 64") String username,
        @NotBlank(message = "password 不能为空")
        @Size(min = 6, max = 128, message = "password 长度需在 6 到 128 之间") String password
) {
}
