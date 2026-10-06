package com.aicode.gateway.infrastructure.logging;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关链路 ID 过滤器（Week 19）。
 *
 * <p>解析优先级（与平台 {@code TraceIdFilter} 同口径）：</p>
 * <ol>
 *   <li>请求头 {@code X-Trace-Id}（Week 16 契约：客户端传什么就用什么）；</li>
 *   <li>合法 W3C {@code traceparent} 的 trace-id；</li>
 *   <li>都没有 → 新生成 32 位 hex。</li>
 * </ol>
 *
 * <p>随后把结果写进响应头 {@code X-Trace-Id}、MDC（日志格式沿用 {@code %X{traceId}}）
 * 与请求属性；并<b>派生</b> {@code traceparent} 注入下游请求，使平台的 OTel 埋点采纳同一条链路
 * （平台 Week 17 的 traceId 单源依赖于此）。</p>
 *
 * <p>注意：MDC 是 ThreadLocal，而响应式链路会跨线程；本过滤器在 {@code doFinally} 中清理，
 * 保证 Netty 线程复用时不会串号（与平台约定一致）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String inbound = GatewayTraceIds.normalize(request.getHeaders().getFirst(GatewayTraceIds.HEADER_NAME));
        String[] parsed = GatewayTraceIds.parseTraceparent(
                request.getHeaders().getFirst(GatewayTraceIds.TRACEPARENT_HEADER));
        String traceId;
        if (!inbound.isEmpty()) {
            traceId = inbound;
        } else if (parsed != null) {
            traceId = parsed[0];
        } else {
            traceId = GatewayTraceIds.newTraceId();
        }

        // 已有合法 traceparent 时原样沿用（上游上下文优先），否则按业务 ID 派生
        String traceparent = parsed != null
                ? GatewayTraceIds.formatTraceparent(parsed[0], parsed[1], "01".equals(parsed[2]))
                : GatewayTraceIds.formatTraceparent(
                        GatewayTraceIds.otelTraceId(traceId), GatewayTraceIds.newSpanId(), true);

        exchange.getAttributes().put(GatewayAttributes.ATTR_TRACE_ID, traceId);
        exchange.getAttributes().put(GatewayAttributes.ATTR_INBOUND_TRACE_ID, inbound);
        exchange.getAttributes().put(GatewayAttributes.ATTR_TRACEPARENT, traceparent);

        // Week 16 契约：X-Trace-Id 原样回显（客户端传什么回什么）
        response.getHeaders().set(GatewayAttributes.TRACE_ID_HEADER, traceId);

        ServerWebExchange effective = exchange;
        if (!traceparent.isEmpty()) {
            // 回写 W3C 头，并把它注入下游请求：平台的 OTel 埋点据此续接同一条链路
            response.getHeaders().set(GatewayAttributes.TRACEPARENT_HEADER, traceparent);
            effective = exchange.mutate()
                    .request(request.mutate()
                            .header(GatewayTraceIds.TRACEPARENT_HEADER, traceparent)
                            .build())
                    .build();
        }

        MDC.put("traceId", traceId);
        return chain.filter(effective).doFinally(signal -> MDC.remove("traceId"));
    }
}
