package com.aicode.patient.domain.exception;

/**
 * Agent 执行异常（如模型输出空白，无法解析出任何步骤）。对外 500。
 */
public class AgentExecutionException extends RuntimeException {

    public AgentExecutionException(String message) {
        super(message);
    }
}
