package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.ExecutionRecord;

/**
 * 执行事件出口（Week 31）。
 *
 * <p>同步端点用 {@link #NONE}（全部空操作）；SSE 端点用 Web 层实现把事件写回浏览器。
 * 实现约定：</p>
 * <ul>
 *   <li>写失败（客户端断开）只置取消标记，<b>不抛异常</b>，不能因为"发不出去"影响执行状态机；</li>
 *   <li>终态只回调一次（{@link #isTerminal()} 用于兜底）；</li>
 *   <li>{@link #isCancelled()} 为 true 时，模型层应尽快停止生成。</li>
 * </ul>
 */
public interface RunEventSink {

    /** 同步执行/无事件出口。 */
    RunEventSink NONE = new RunEventSink() {
        @Override
        public void onStarted(ExecutionRecord running) {
        }

        @Override
        public void onDelta(String text) {
        }

        @Override
        public void onCompleted(ExecutionRecord terminal) {
        }

        @Override
        public void onFailed(ExecutionRecord terminal, RuntimeException error) {
        }

        @Override
        public void onCancelled(ExecutionRecord terminal) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    };

    /**
     * 已落 RUNNING 记录、开始执行。
     *
     * @param running 运行中记录
     */
    void onStarted(ExecutionRecord running);

    /**
     * 一段可见增量（已脱敏）。
     *
     * @param text 增量文本
     */
    void onDelta(String text);

    /**
     * 成功终态（COMPLETED 或 PENDING_APPROVAL）。
     *
     * @param terminal 终态记录
     */
    void onCompleted(ExecutionRecord terminal);

    /**
     * 失败终态。
     *
     * @param terminal 失败记录
     * @param error    原始异常（用于错误码映射）
     */
    void onFailed(ExecutionRecord terminal, RuntimeException error);

    /**
     * 调用方取消终态。
     *
     * @param terminal 取消记录
     */
    void onCancelled(ExecutionRecord terminal);

    /**
     * 客户端是否已断开/停止。
     *
     * @return true 表示应停止生成
     */
    boolean isCancelled();

    /**
     * 是否已发出终态事件。
     *
     * @return true 表示已终态
     */
    boolean isTerminal();
}
