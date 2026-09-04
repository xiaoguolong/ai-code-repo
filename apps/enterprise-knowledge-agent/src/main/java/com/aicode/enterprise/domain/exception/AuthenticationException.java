package com.aicode.enterprise.domain.exception;

/**
 * 认证失败（用户名不存在或密码错误）。映射为 HTTP 401。
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
