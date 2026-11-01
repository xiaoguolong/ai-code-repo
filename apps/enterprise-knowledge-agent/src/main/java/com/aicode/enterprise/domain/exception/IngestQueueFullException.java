package com.aicode.enterprise.domain.exception;

/**
 * 入库队列已满（Week 32）。接口层映射为 429，提示稍后重试。
 */
public class IngestQueueFullException extends RuntimeException {

    /**
     * @param message 说明
     */
    public IngestQueueFullException(String message) {
        super(message);
    }
}
