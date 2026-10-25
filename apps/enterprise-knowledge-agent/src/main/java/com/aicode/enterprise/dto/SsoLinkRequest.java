package com.aicode.enterprise.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * SSO 映射建链/重建请求（Week 23，运维令牌）。
 *
 * @param platformUserId   平台用户主键
 * @param enterpriseUserId 本地用户主键
 * @param tenantKey        租户键，可空（空则 default）
 */
public record SsoLinkRequest(
        @NotNull(message = "platformUserId must not be null")
        @Positive(message = "platformUserId must be positive")
        Long platformUserId,
        @NotNull(message = "enterpriseUserId must not be null")
        @Positive(message = "enterpriseUserId must be positive")
        Long enterpriseUserId,
        @Size(max = 64, message = "tenantKey must not exceed 64 characters")
        String tenantKey
) {
}
