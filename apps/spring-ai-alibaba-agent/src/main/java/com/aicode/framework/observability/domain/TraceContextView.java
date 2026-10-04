package com.aicode.framework.observability.domain;

/**
 * 当前请求的链路上下文视图（Week 17）。
 *
 * <p>用于把「日志里的 traceId」与「链路后端里的 OTel traceId」对照起来：
 * 两个 ID 由 {@code TraceIds} 确定性换算，因此可以互相推导、互相验证。</p>
 *
 * @param traceId              自研关联键，与响应头 {@code X-Trace-Id}、响应体 {@code traceId} 一致
 * @param otelTraceId          32 位小写 hex 的 OTel 链路 ID
 * @param spanId               16 位小写 hex 的当前 span ID
 * @param traceparent          可直接复制给下游的 W3C 头值；观测关闭时为空串
 * @param sampled              当前链路是否被采样
 * @param observabilityEnabled 观测开关状态
 */
public record TraceContextView(
        String traceId,
        String otelTraceId,
        String spanId,
        String traceparent,
        boolean sampled,
        boolean observabilityEnabled
) {

    /** 观测关闭时的视图：只保留自研 traceId，其余为空。 */
    public static TraceContextView disabled(String traceId) {
        return new TraceContextView(traceId, "", "", "", false, false);
    }
}
