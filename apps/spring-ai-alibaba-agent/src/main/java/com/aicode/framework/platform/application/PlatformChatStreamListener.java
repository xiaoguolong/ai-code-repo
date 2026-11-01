package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.ChatStreamListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把模型增量转成执行事件（Week 31）：脱敏 → 记录首字时间 → 写 sink。
 *
 * <p>取消由 sink 决定：客户端断开后 {@link #isCancelled()} 变 true，
 * 适配器用 {@code takeWhile} 退订上游模型流。</p>
 */
final class PlatformChatStreamListener implements ChatStreamListener {

    private final RunEventSink sink;
    private final StreamingTextSanitizer sanitizer;
    private final AtomicLong firstDeltaAtNanos = new AtomicLong();

    PlatformChatStreamListener(RunEventSink sink, StreamingTextSanitizer sanitizer) {
        this.sink = sink;
        this.sanitizer = sanitizer;
    }

    @Override
    public void onDelta(String delta) {
        String safe = sanitizer.accept(delta);
        if (safe.isEmpty()) {
            return;
        }
        firstDeltaAtNanos.compareAndSet(0, System.nanoTime());
        sink.onDelta(safe);
    }

    @Override
    public boolean isCancelled() {
        return sink.isCancelled();
    }

    /**
     * 模型生成结束：flush 尾部（可能为空），之后由调用方落终态。
     */
    void finish() {
        String tail = sanitizer.flush();
        if (!tail.isEmpty()) {
            firstDeltaAtNanos.compareAndSet(0, System.nanoTime());
            sink.onDelta(tail);
        }
    }

    /**
     * 首段可见增量的时间点（{@link System#nanoTime()}，0 表示没有增量）。
     *
     * @return 纳秒时间点
     */
    long firstDeltaAtNanos() {
        return firstDeltaAtNanos.get();
    }
}
