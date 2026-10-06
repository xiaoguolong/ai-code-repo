package com.aicode.gateway.domain.exception;

import com.aicode.gateway.dto.GatewayErrorCode;

/**
 * 未携带会话 / 会话已过期（HTTP 401）。
 */
public class GatewayUnauthorizedException extends GatewayException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 服务端日志说明（不对外透出细节）
     */
    public GatewayUnauthorizedException(String message) {
        super(GatewayErrorCode.UNAUTHORIZED, message);
    }
}
