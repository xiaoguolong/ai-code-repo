package com.aicode.gateway.application;

import com.aicode.gateway.domain.model.GatewayStatusView;
import com.aicode.gateway.domain.port.GatewayCachePort;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import com.aicode.gateway.domain.port.PlatformClientPort;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.observability.SkyWalkingAttachmentProbe;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 网关接入自检用例（Week 19）。
 *
 * <p>把「路由打到哪 / 会话与缓存是否活着 / 这条请求的链路 ID / SkyWalking Agent 挂没挂」
 * 一次答完，全部为只读操作，<b>不返回任何密钥或平台 token</b>。</p>
 */
@Service
public class GatewayStatusUseCase {

    private final GatewayProperties routes;
    private final GatewaySessionProperties sessionProperties;
    private final GatewayCatalogProperties catalogProperties;
    private final GatewaySessionPort sessions;
    private final GatewayCachePort cache;
    private final PlatformClientPort platformClient;
    private final SkyWalkingAttachmentProbe skyWalkingProbe;

    /**
     * @param routes            路由配置
     * @param sessionProperties 会话配置
     * @param catalogProperties 缓存配置
     * @param sessions          会话端口
     * @param cache             缓存端口
     * @param platformClient    平台出站端口（只读取基础地址）
     * @param skyWalkingProbe   Agent 挂载探针
     */
    public GatewayStatusUseCase(
            GatewayProperties routes,
            GatewaySessionProperties sessionProperties,
            GatewayCatalogProperties catalogProperties,
            GatewaySessionPort sessions,
            GatewayCachePort cache,
            PlatformClientPort platformClient,
            SkyWalkingAttachmentProbe skyWalkingProbe
    ) {
        this.routes = routes;
        this.sessionProperties = sessionProperties;
        this.catalogProperties = catalogProperties;
        this.sessions = sessions;
        this.cache = cache;
        this.platformClient = platformClient;
        this.skyWalkingProbe = skyWalkingProbe;
    }

    /**
     * 读取自检视图。
     *
     * @param traceId        本次请求的业务链路 ID
     * @param traceparent    本次请求透传给下游的 W3C 上下文，可为空串
     * @param headerName     链路 ID 头名
     * @param inboundTraceId 客户端带来的原始 X-Trace-Id，可为空串
     * @return 自检视图
     */
    public Mono<GatewayStatusView> status(
            String traceId, String traceparent, String headerName, String inboundTraceId) {
        return Mono.zip(
                        sessions.list().onErrorReturn(java.util.List.of()),
                        cache.countByPrefix(catalogProperties.resolvedKeyPrefix()).onErrorReturn(0L))
                .map(tuple -> new GatewayStatusView(
                        "agent-gateway",
                        new GatewayStatusView.RouteTarget(
                                routes.resolvedPlatformUri(),
                                routes.resolvedPlatformProxyPath(),
                                routes.resolvedGatewayApiPath()),
                        new GatewayStatusView.Sessions(
                                sessionProperties.resolvedStorage(),
                                tuple.getT1().size(),
                                sessionProperties.resolvedTtlSeconds(),
                                tuple.getT2(),
                                catalogProperties.resolvedCacheTtlSeconds()),
                        new GatewayStatusView.Tracing(
                                nullToEmpty(traceId), nullToEmpty(traceparent),
                                nullToEmpty(headerName), nullToEmpty(inboundTraceId)),
                        skyWalkingProbe.attached()));
    }

    /**
     * 平台基础地址（供状态视图之外的日志/排查使用）。
     *
     * @return 平台服务基础地址
     */
    public String platformBaseUri() {
        return platformClient.baseUri();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
