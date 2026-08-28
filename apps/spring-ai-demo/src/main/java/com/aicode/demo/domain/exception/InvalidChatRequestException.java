package com.aicode.demo.domain.exception;

/**
 * 聊天入参不合法（空白消息等）。映射为 HTTP 400。
 */
public class InvalidChatRequestException extends RuntimeException {

    public InvalidChatRequestException(String message) {
        super(message);
    }
}
