package com.aicode.core.domain.exception;

/**
 * 向量化失败。由 EmbeddingModelPort 实现抛出，对外映射为 502，不暴露供应商报文。
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
