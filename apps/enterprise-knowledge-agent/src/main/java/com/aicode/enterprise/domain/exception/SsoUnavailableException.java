package com.aicode.enterprise.domain.exception;

/**
 * 统一身份源不可用（Week 21）。
 *
 * <p>语义与「凭据错误」严格区分：平台超时/5xx 必须是 503
 * {@code AUTH_SSO_UNAVAILABLE}，不能伪装成 401——否则用户会反复改密码，
 * 而真正的问题在平台侧（第 19 周「报错信息指向的位置是错的」同类教训）。</p>
 */
public class SsoUnavailableException extends RuntimeException {

    /**
     * @param message 内部原因（不返回给客户端）
     */
    public SsoUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message 内部原因（不返回给客户端）
     * @param cause   底层异常
     */
    public SsoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
