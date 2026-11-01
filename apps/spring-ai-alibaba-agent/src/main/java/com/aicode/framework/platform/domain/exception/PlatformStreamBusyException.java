package com.aicode.framework.platform.domain.exception;

/**
 * 流式并发已满（Week 31）。
 *
 * <p>对外 429，且在<b>建执行记录之前</b>返回：不产生"排队中"的 RUNNING 僵尸记录。</p>
 */
public class PlatformStreamBusyException extends RuntimeException {

    public PlatformStreamBusyException(String message) {
        super(message);
    }
}
