package com.aicode.demo.domain.exception;

/**
 * OCR 识别失败。由 DocumentOcrPort 实现抛出，对外映射为 502，不暴露供应商报文。
 */
public class OcrException extends RuntimeException {

    /**
     * @param message 领域层可见的错误描述
     */
    public OcrException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   供应商/网络根因，仅供服务端日志
     */
    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
