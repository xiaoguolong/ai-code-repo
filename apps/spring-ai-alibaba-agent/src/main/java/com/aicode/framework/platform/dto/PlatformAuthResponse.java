package com.aicode.framework.platform.dto;

/**
 * 平台登录响应。
 */
public record PlatformAuthResponse(String token, long userId, String username, String roleKey) {
}
