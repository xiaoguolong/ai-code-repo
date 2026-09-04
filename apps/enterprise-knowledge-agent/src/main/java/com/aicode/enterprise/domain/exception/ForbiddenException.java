package com.aicode.enterprise.domain.exception;

/**
 * 越权访问：访问不属于当前用户的知识库/文档/会话。映射为 HTTP 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
