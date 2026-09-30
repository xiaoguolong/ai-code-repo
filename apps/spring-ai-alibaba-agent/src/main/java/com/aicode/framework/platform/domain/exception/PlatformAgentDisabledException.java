package com.aicode.framework.platform.domain.exception;

/**
 * Agent 已禁用，不可调度。
 */
public class PlatformAgentDisabledException extends RuntimeException {

    public PlatformAgentDisabledException(String message) {
        super(message);
    }
}
