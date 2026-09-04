package com.aicode.enterprise.domain.exception;

/**
 * 资源冲突（如用户名已存在）。映射为 HTTP 409。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
