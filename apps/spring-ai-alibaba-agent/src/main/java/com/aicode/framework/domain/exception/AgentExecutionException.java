package com.aicode.framework.domain.exception;

/**
 * Graph Agent 执行异常（模型输出空白等）。对外映射为 500。
 */
public class AgentExecutionException extends RuntimeException {

    public AgentExecutionException(String message) {
        super(message);
    }
}
