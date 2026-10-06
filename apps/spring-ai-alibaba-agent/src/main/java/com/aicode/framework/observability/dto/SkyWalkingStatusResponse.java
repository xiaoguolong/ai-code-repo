package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.SkyWalkingStatusView;

/**
 * SkyWalking 接入自检响应（Week 19）。
 *
 * <p>字段即 {@link SkyWalkingStatusView}，<b>类型上没有</b>任何密钥字段
 * （Agent 与 OAP 之间不需要密钥），因此不存在「漏改导致回显密钥」的风险。</p>
 *
 * @param enabled           应用侧手动埋点开关
 * @param bridgeAvailable   Toolkit 是否可用（Agent 是否真的挂上）
 * @param serviceName       服务名
 * @param backendService    OAP 后端地址
 * @param skywalkingTraceId 本次请求的 SkyWalking traceId
 * @param skywalkingSpanId  当前 SkyWalking spanId
 * @param traceId           业务 traceId（与响应头一致）
 * @param otelTraceId       OTel（W3C）traceId
 * @param correlationTag    跨后端关联标签名
 * @param correlated        两套链路是否都记录了本次请求
 */
public record SkyWalkingStatusResponse(
        boolean enabled,
        boolean bridgeAvailable,
        String serviceName,
        String backendService,
        String skywalkingTraceId,
        int skywalkingSpanId,
        String traceId,
        String otelTraceId,
        String correlationTag,
        boolean correlated
) {

    /**
     * 领域视图 → 响应体。
     *
     * @param view 自检视图
     * @return 响应体
     */
    public static SkyWalkingStatusResponse from(SkyWalkingStatusView view) {
        return new SkyWalkingStatusResponse(
                view.enabled(),
                view.bridgeAvailable(),
                view.serviceName(),
                view.backendService(),
                view.skywalkingTraceId(),
                view.skywalkingSpanId(),
                view.traceId(),
                view.otelTraceId(),
                view.correlationTag(),
                view.correlated());
    }
}
