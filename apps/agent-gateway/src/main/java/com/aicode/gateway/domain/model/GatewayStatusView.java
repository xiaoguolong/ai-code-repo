package com.aicode.gateway.domain.model;

/**
 * 网关接入自检视图（Week 19）。
 *
 * <p>回答四个运维问题：路由打到哪、当前有没有会话垫底、这条请求的链路 ID 是什么、
 * SkyWalking Agent 是否挂在启动参数上。<b>不含任何密钥或平台 token。</b></p>
 *
 * @param application     应用名
 * @param routes          路由目标（见 {@link RouteTarget}）
 * @param sessions        会话存储概况（见 {@link Sessions}）
 * @param tracing         链路上下文（见 {@link Tracing}）
 * @param skywalkingAgent 是否检测到 SkyWalking Agent（由启动参数 / Toolkit 可用性判定）
 */
public record GatewayStatusView(
        String application,
        RouteTarget routes,
        Sessions sessions,
        Tracing tracing,
        boolean skywalkingAgent
) {

    /**
     * 路由目标。
     *
     * @param platformUri        平台服务基础地址（Feign 出口）
     * @param platformProxyPath  透传代理路径前缀
     * @param gatewayApiPath     网关自有用例路径前缀
     */
    public record RouteTarget(String platformUri, String platformProxyPath, String gatewayApiPath) {
    }

    /**
     * 会话存储概况。
     *
     * @param store              存储实现（如 {@code redis}）
     * @param activeSessions     当前活跃会话数
     * @param ttlSeconds         新会话有效期（秒）
     * @param catalogCached      目录缓存条目数
     * @param catalogTtlSeconds  目录缓存有效期（秒）
     */
    public record Sessions(
            String store,
            long activeSessions,
            long ttlSeconds,
            long catalogCached,
            long catalogTtlSeconds
    ) {
    }

    /**
     * 链路上下文。
     *
     * @param traceId      本次请求的业务链路 ID（与响应头 {@code X-Trace-Id} 一致）
     * @param traceparent  本次请求透传给下游的 W3C 上下文（无则空串）
     * @param headerName   业务链路 ID 的请求/响应头名
     * @param inboundTraceId 客户端带来的原始 X-Trace-Id（未带则为空串）
     */
    public record Tracing(String traceId, String traceparent, String headerName, String inboundTraceId) {
    }
}
