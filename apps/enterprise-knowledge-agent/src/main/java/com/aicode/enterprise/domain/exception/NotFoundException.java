package com.aicode.enterprise.domain.exception;

/**
 * 目标资源不存在（知识库/文档/会话/文件等）。映射为 HTTP 404。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
