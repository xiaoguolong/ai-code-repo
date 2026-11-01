package com.aicode.core.domain.model;

/**
 * 模型增量输出的回调（Week 31）。
 *
 * <p>同步调用用 {@code ChatModelPort.chat(...)}；需要"边生成边显示"时用
 * {@code chatStream(...)}，实现方每收到一段增量就调用 {@link #onDelta(String)}，
 * 方法结束时返回累加后的完整结果。约定：</p>
 * <ul>
 *   <li>同一线程内<b>顺序</b>回调，不会并发；</li>
 *   <li>增量文本可能为空串（例如仅携带 usage 的末包），调用方应忽略空串；</li>
 *   <li>{@link #isCancelled()} 返回 true 后实现方应尽快停止并抛取消异常，
 *       而不是返回一个"看起来成功"的结果；</li>
 *   <li>实现方不得在回调里做阻塞 IO（Web 层只负责把事件写出去）。</li>
 * </ul>
 */
public interface ChatStreamListener {

    /**
     * 收到一段模型增量文本。
     *
     * @param delta 增量片段，可能为空串
     */
    void onDelta(String delta);

    /**
     * 调用方是否已取消（客户端断开、用户点"停止"、SSE 超时）。
     *
     * <p>默认不取消，便于只关心增量的调用方直接实现。</p>
     *
     * @return true 表示应尽快停止上游生成
     */
    default boolean isCancelled() {
        return false;
    }
}
