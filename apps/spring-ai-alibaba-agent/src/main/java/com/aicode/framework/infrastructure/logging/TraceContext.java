package com.aicode.framework.infrastructure.logging;

/**
 * W3C Trace Context 解析结果（Week 17）。
 *
 * <p>对应头 {@code traceparent: 00-<32hex trace-id>-<16hex parent-id>-<2hex flags>}。
 * 非法或缺失时返回 {@link #invalid()}，由调用方回退到自研 traceId。</p>
 *
 * @param valid        是否为合法 W3C traceparent
 * @param traceId      32 位小写 hex 的 trace-id
 * @param parentSpanId 16 位小写 hex 的 parent-id
 * @param sampled      采样标志（flags 最低位）
 */
public record TraceContext(boolean valid, String traceId, String parentSpanId, boolean sampled) {

    /** 解析失败时的占位值，各字段为空串、{@code sampled=false}。 */
    public static TraceContext invalid() {
        return new TraceContext(false, "", "", false);
    }
}
