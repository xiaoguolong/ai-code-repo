package com.aicode.patient.domain.exception;

/**
 * 模型调用失败。对外统一 502，不把厂商报文和堆栈返回给客户端。
 */
public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
