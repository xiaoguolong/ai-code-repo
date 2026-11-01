package com.aicode.core.domain.exception;

/**
 * 流式输出被调用方取消（客户端断开、用户停止、SSE 超时）。
 *
 * <p><b>刻意不继承</b> {@link ChatModelException}：取消不是模型故障，
 * 不能被"模型不可用就降级为摘录"的逻辑吞掉，否则会把一次用户主动停止
 * 记成一次成功（降级）执行。平台层单独捕获本异常并落 {@code CANCELLED}。</p>
 */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException(String message) {
        super(message);
    }
}
