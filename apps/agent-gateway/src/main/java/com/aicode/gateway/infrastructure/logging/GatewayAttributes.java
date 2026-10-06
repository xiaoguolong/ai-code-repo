package com.aicode.gateway.infrastructure.logging;

/**
 * 网关请求属性与链路头常量（Week 19）。
 *
 * <p>集中定义，避免魔法字符串散落（规范 5.5）。这些键是网关内部约定：
 * 过滤器写入、用例与 Controller 读取。</p>
 */
public final class GatewayAttributes {

    /** 业务链路 ID 的请求/响应头名（与平台一致，Week 16 契约）。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** W3C Trace Context 头名。 */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /** 标准授权头名。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 请求属性：本次请求的业务链路 ID。 */
    public static final String ATTR_TRACE_ID = "gateway.traceId";

    /** 请求属性：客户端带来的原始 X-Trace-Id（未带为空串）。 */
    public static final String ATTR_INBOUND_TRACE_ID = "gateway.inboundTraceId";

    /** 请求属性：本次请求透传给下游的 W3C traceparent。 */
    public static final String ATTR_TRACEPARENT = "gateway.traceparent";

    /** 请求属性：解析出的调用者身份（{@code SessionPrincipal}）。 */
    public static final String ATTR_PRINCIPAL = "gateway.principal";

    private GatewayAttributes() {
    }
}
