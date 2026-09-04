package com.aicode.enterprise.application;

/**
 * 登录用例入参。
 *
 * @param username 用户名
 * @param password 明文密码
 */
public record LoginUserCommand(String username, String password) {
}
