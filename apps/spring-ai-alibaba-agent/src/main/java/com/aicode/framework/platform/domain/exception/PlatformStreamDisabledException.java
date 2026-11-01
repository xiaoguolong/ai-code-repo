package com.aicode.framework.platform.domain.exception;

/**
 * 流式输出被关闭（Week 31，{@code PLATFORM_STREAM_ENABLED=false}）。
 *
 * <p>对外 503 + {@code STREAM_DISABLED}：前端看到它应回退同步端点，而不是报错给用户。</p>
 */
public class PlatformStreamDisabledException extends RuntimeException {

    public PlatformStreamDisabledException(String message) {
        super(message);
    }
}
