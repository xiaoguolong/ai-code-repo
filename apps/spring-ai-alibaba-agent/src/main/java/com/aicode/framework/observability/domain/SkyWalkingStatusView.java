package com.aicode.framework.observability.domain;

/**
 * SkyWalking 接入自检视图（Week 19）。
 *
 * <p>回答运维最常问的四个问题：开关开了吗、Agent 挂上了吗、数据发到哪个 OAP、
 * 这条请求在 SkyWalking 里的 traceId 是多少（以及它对应的业务 traceId）。
 * <b>不含任何密钥</b>。</p>
 *
 * @param enabled        应用侧手动埋点开关（{@code observability.skywalking.enabled}）
 * @param bridgeAvailable Toolkit 是否可用（等价于「Agent 是否真的挂上了」）
 * @param serviceName    服务名（{@code SW_AGENT_NAME}）
 * @param backendService OAP 后端地址（{@code SW_AGENT_COLLECTOR_BACKEND_SERVICES}）
 * @param skywalkingTraceId 本次请求的 SkyWalking traceId（Base64 segmentId）
 * @param skywalkingSpanId  当前 SkyWalking spanId
 * @param traceId        业务 traceId（与响应头 {@code X-Trace-Id} 一致）
 * @param otelTraceId    OTel（W3C）traceId，用于与 Langfuse / 通用 collector 对照
 * @param correlationTag 跨后端关联标签名（值即 {@code traceId}）
 */
public record SkyWalkingStatusView(
        boolean enabled,
        boolean bridgeAvailable,
        String serviceName,
        String backendService,
        String skywalkingTraceId,
        int skywalkingSpanId,
        String traceId,
        String otelTraceId,
        String correlationTag
) {

    /**
     * 两个 ID 是否指向同一次请求（用于自检页一眼判断「有没有关联上」）。
     *
     * <p>注意：SkyWalking 的 traceId 与 W3C traceId <b>不可能相等</b>，
     * 因此这里判断的是「两个 ID 都非空」，即「两套链路都记录了这次请求」。</p>
     *
     * @return 两套 ID 都非空返回 true
     */
    public boolean correlated() {
        return skywalkingTraceId != null && !skywalkingTraceId.isBlank()
                && traceId != null && !traceId.isBlank();
    }
}
