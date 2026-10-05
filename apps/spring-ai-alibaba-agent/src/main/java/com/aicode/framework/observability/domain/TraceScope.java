package com.aicode.framework.observability.domain;

/**
 * trace 维度的作用域句柄（Week 18）。
 *
 * <p>与 {@link SpanScope} 同构：由 {@link AgentObservabilityPort#beginTrace} 创建，
 * 调用方用 try-with-resources 保证一定退出作用域，避免线程复用时把上一个请求的 Langfuse 维度
 * 带到下一个请求（Tomcat 线程池复用是真实存在的）。</p>
 *
 * <p>约定：{@link #close()} 必须幂等，并恢复到进入本作用域之前的维度。</p>
 */
public interface TraceScope extends AutoCloseable {

    /**
     * 退出作用域并恢复进入前的维度。必须幂等。
     */
    @Override
    void close();
}
