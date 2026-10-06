package com.aicode.gateway.infrastructure.security;

import com.aicode.gateway.infrastructure.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关自有用例的统一鉴权过滤器（Week 19）。
 *
 * <p>只作用于 <b>WebFlux 自己的 handler</b>（网关的 Controller）。透传到平台的代理路由
 * 由 Spring Cloud Gateway 的 {@code RoutePredicateHandlerMapping} 处理，<b>不会</b>经过
 * {@link WebFilter}，那部分鉴权由 {@link GatewayAuthGlobalFilter} 负责（两者共用
 * {@link GatewayAuthSupport}，逻辑只有一份）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilter.class);

    private final GatewayAuthSupport support;
    private final String gatewayApiPath;

    /**
     * @param support 鉴权支撑
     * @param routes  路由配置（取网关自有用例前缀）
     */
    public GatewayAuthFilter(GatewayAuthSupport support, GatewayProperties routes) {
        this.support = support;
        this.gatewayApiPath = routes.resolvedGatewayApiPath();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // 只作用于网关自有用例；代理路由由 GatewayAuthGlobalFilter 负责（见类注释）
        if (!path.startsWith(gatewayApiPath) || support.isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        String token = support.extractToken(exchange.getRequest());
        if (token.isEmpty()) {
            log.info("[gateway] 鉴权失败：缺少会话 token path={}", path);
            return support.unauthorized(exchange, "缺少会话 token");
        }
        return support.resolve(token)
                .flatMap(session -> chain.filter(support.authenticated(exchange, token, session)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[gateway] 鉴权失败：会话不存在或已过期 path={}", path);
                    return support.unauthorized(exchange, "会话不存在或已失效");
                }));
    }
}
