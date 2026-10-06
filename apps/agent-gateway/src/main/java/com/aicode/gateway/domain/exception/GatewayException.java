package com.aicode.gateway.domain.exception;

import com.aicode.gateway.dto.GatewayErrorCode;

/**
 * 网关领域异常基类（Week 19）。
 *
 * <p>只承载「对外的错误码 + 安全文案」，不承载上游原始报文：
 * 堆栈与上游细节只进服务端日志（规范 5.7）。</p>
 */
public class GatewayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final GatewayErrorCode errorCode;

    /**
     * @param errorCode 对外错误码
     * @param message   仅用于服务端日志与兜底文案的说明
     */
    public GatewayException(GatewayErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 对外错误码
     * @param message   服务端日志说明
     * @param cause     原因
     */
    public GatewayException(GatewayErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 对外错误码。
     *
     * @return 错误码枚举
     */
    public GatewayErrorCode errorCode() {
        return errorCode;
    }
}
