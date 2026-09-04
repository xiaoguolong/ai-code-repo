package com.aicode.enterprise.application;

/**
 * 注册用户用例出参。
 *
 * @param userId   用户主键
 * @param username 用户名
 */
public record RegisterUserOutcome(Long userId, String username) {
}
