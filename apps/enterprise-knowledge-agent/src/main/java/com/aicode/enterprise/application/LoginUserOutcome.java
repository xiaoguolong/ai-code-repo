package com.aicode.enterprise.application;

/**
 * 登录用例出参。
 *
 * @param token    令牌值
 * @param userId   用户主键
 * @param username 用户名
 */
public record LoginUserOutcome(String token, Long userId, String username) {
}
