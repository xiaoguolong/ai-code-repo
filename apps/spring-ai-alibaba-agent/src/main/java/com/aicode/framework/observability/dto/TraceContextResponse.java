package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.TraceContextView;

/**
 * 链路上下文响应体（Week 17）。
 *
 * <p>字段全部为链路标识，不含任何业务内容，可安全暴露给已登录用户。</p>
 *
 * @param traceId              自研关联键，与响应头 {@code X-Trace-Id} 一致
 * @param otelTraceId          32 位小写 hex 的 OTel 链路 ID
 * @param spanId               16 位小写 hex 的当前 span ID
 * @param traceparent          W3C 头值，可直接用于下游请求
 * @param sampled              当前链路是否被采样
 * @param observabilityEnabled 观测开关是否打开
 */
public record TraceContextResponse(
        String traceId,
        String otelTraceId,
        String spanId,
        String traceparent,
        boolean sampled,
        boolean observabilityEnabled
) {

    /**
     * 由领域视图转换（Controller 只做协议转换，不承载判断逻辑）。
     *
     * @param view 领域视图，非 null
     * @return 响应体
     */
    public static TraceContextResponse from(TraceContextView view) {
        return new TraceContextResponse(
                view.traceId(),
                view.otelTraceId(),
                view.spanId(),
                view.traceparent(),
                view.sampled(),
                view.observabilityEnabled());
    }
}
