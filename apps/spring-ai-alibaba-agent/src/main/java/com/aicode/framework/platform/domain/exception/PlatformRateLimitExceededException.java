package com.aicode.framework.platform.domain.exception;

/**
 * 登录/身份校验被限流（Week 22，HTTP 429）。
 *
 * <p>语义与 401 严格区分：401 是「凭据不对」，429 是「试得太频繁」。
 * 混用会让客户端反复重试，等于帮攻击者把限流打满。</p>
 */
public class PlatformRateLimitExceededException extends RuntimeException {

    /**
     * @param message 内部原因（对外统一用 {@code RATE_LIMITED} 文案）
     */
    public PlatformRateLimitExceededException(String message) {
        super(message);
    }
}
