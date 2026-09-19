package com.aicode.core.domain.exception;

/**
 * OCR 识别失败。由 DocumentOcrPort 实现抛出，对外映射为 502，不暴露供应商报文。
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
