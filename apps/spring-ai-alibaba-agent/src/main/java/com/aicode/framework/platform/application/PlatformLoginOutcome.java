package com.aicode.framework.platform.application;

/**
 * 平台登录结果。
 */
public record PlatformLoginOutcome(String token, long userId, String username, String roleKey) {
}
