package com.aicode.core.domain.exception;

/**
 * 工具执行失败（未知工具名、参数非法或工具内部错误）。由 ToolPort 实现抛出，对外映射为 502。
 */
public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
