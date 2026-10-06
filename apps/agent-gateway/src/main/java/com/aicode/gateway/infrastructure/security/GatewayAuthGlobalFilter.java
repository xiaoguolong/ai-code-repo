package com.aicode.gateway.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 代理路由的统一鉴权全局过滤器（Week 19）。
 *
 * <p><b>为什么必须有它</b>：Spring Cloud Gateway 的路由请求由
 * {@code RoutePredicateHandlerMapping} 处理，{@code WebFilter} 链<b>不会</b>执行。
 * 若只写 {@link GatewayAuthFilter}，透传到平台的路径会完全绕过鉴权 ——
 * 这类「以为拦住了其实没拦」是网关接入里最危险的疏漏，故这里显式覆盖并加单测锁死。</p>
 *
 * <p><b>为什么这里不做 {@code exchange.mutate()}（实测踩坑，排查成本极高）</b>：
 * 在真实 Netty 容器里，mutate 派生出的交换对象其响应是<b>只读</b>的
 * （{@code ReadOnlyHttpHeaders}）。代理路由最终要把上游响应写出去，只要碰到这个派生对象就会抛
 * {@code UnsupportedOperationException: setContentLength}，现象是
 * <b>「平台侧明明 200、客户端却收到连接被关闭」</b>，日志里只有一句
 * {@code Error finishing response. Closing connection}，极易误判成网络问题。
 * 而对网关自有用例（Controller）而言，响应由 Controller 自己写，走 WebFilter 那条路径没有这个问题。</p>
 *
 * <p>因此代理路径只做「校验 + 记录身份」，<b>不改写请求</b>：
 * 路由目标由路由配置决定（{@code uri: http://...}），平台侧按 {@code Authorization: Bearer} 头
 * 自行鉴权（平台已配 {@code sa-token.token-name=Authorization}）。
 * 这样既保住了鉴权，也不引入只读响应。</p>
 *
 * <p>顺序取 {@link Ordered#HIGHEST_PRECEDENCE}：必须早于 {@code NettyRoutingFilter}
 * 发出上游请求，未鉴权的请求才不会打到后端。</p>
 */
@Component
public class GatewayAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthGlobalFilter.class);

    private final GatewayAuthSupport support;

    /**
     * @param support 鉴权支撑
     */
    public GatewayAuthGlobalFilter(GatewayAuthSupport support) {
        this.support = support;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (support.isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        String token = support.extractToken(exchange.getRequest());
        if (token.isEmpty()) {
            log.info("[gateway] 代理路由鉴权失败：缺少会话 token path={}", path);
            return support.unauthorized(exchange, "缺少会话 token");
        }
        return support.resolve(token)
                .flatMap(session -> chain.filter(support.registerPrincipal(exchange, token, session)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[gateway] 代理路由鉴权失败：会话不存在或已过期 path={}", path);
                    return support.unauthorized(exchange, "会话不存在或已失效");
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
