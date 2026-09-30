package com.aicode.framework.platform.domain.exception;

/**
 * 平台权限不足（403）。
 */
public class PlatformAccessDeniedException extends RuntimeException {

    public PlatformAccessDeniedException(String message) {
        super(message);
    }
}
