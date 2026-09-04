package com.aicode.enterprise.application;

/**
 * 注册用户用例入参。
 *
 * @param username 用户名
 * @param password 明文密码，由用例层哈希后落库
 */
public record RegisterUserCommand(String username, String password) {
}
