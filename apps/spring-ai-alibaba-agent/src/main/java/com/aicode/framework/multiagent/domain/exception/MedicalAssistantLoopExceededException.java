package com.aicode.framework.multiagent.domain.exception;

/**
 * Supervisor 调度超过最大轮次。
 */
public class MedicalAssistantLoopExceededException extends RuntimeException {

    public MedicalAssistantLoopExceededException(String message) {
        super(message);
    }
}
