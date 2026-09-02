package com.aicode.demo.domain.exception;

/**
 * 要求 JSON 输出但模型返回无法解析为对象。映射为 HTTP 422。
 */
public class StructuredOutputException extends RuntimeException {

    public StructuredOutputException(String message) {
        super(message);
    }
}
