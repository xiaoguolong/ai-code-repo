package com.aicode.framework.domain.exception;

/**
 * Graph Agent 超过最大迭代次数仍未收敛。对外映射为 500，防止死循环烧钱。
 */
public class AgentLoopExceededException extends RuntimeException {

    public AgentLoopExceededException(String message) {
        super(message);
    }
}
