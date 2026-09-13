package com.aicode.patient.domain.exception;

/**
 * 入参不合法（空白任务、Prompt 模板缺失等）。映射为 HTTP 400。
 */
public class InvalidChatRequestException extends RuntimeException {

    public InvalidChatRequestException(String message) {
        super(message);
    }
}
