package com.aicode.framework.platform.domain.exception;

/**
 * 平台资源冲突（如 agentKey 重复）。
 */
public class PlatformConflictException extends RuntimeException {

    public PlatformConflictException(String message) {
        super(message);
    }
}
