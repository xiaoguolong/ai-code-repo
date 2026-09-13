package com.aicode.patient.domain.exception;

/**
 * Agent 循环达到最大迭代次数仍未收敛。对外 500，防止死循环烧钱。
 */
public class AgentLoopExceededException extends RuntimeException {

    public AgentLoopExceededException(String message) {
        super(message);
    }
}
