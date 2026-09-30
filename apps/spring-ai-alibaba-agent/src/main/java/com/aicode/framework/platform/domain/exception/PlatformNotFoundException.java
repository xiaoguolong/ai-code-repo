package com.aicode.framework.platform.domain.exception;

/**
 * 平台资源不存在。
 */
public class PlatformNotFoundException extends RuntimeException {

    public PlatformNotFoundException(String message) {
        super(message);
    }
}
