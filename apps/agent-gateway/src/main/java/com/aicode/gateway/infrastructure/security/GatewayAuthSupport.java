package com.aicode.gateway.infrastructure.security;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import com.aicode.gateway.dto.ApiResponse;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 网关鉴权支撑（Week 19）。
 *
 * <p>网关有两条请求路径，鉴权逻辑必须完全一致，故把「放行判定 / 取 token / 查会话 /
 * 换发上游凭据 / 写 401 信封」收口到本类：</p>
 * <ul>
 *   <li>网关自有用例（Controller）：由 {@link GatewayAuthFilter}（WebFilter）调用；</li>
 *   <li>透传到平台的代理路由：由 {@link GatewayAuthGlobalFilter}（GlobalFilter）调用
 *       —— 路由由 Gateway 的 {@code RoutePredicateHandlerMapping} 处理，WebFilter 不会执行，
 *       这是网关实现里最容易漏掉的一点（漏了就是「代理路径完全没有鉴权」）。</li>
 * </ul>
 */
@Component
public class GatewayAuthSupport {

    private final GatewaySessionPort sessions;
    private final GatewaySessionProperties sessionProperties;
    private final GatewayProperties routes;
    private final ObjectMapper objectMapper;

    /**
     * @param sessions          会话端口
     * @param sessionProperties 会话配置
     * @param routes            路由配置（识别网关自有用例路径）
     * @param objectMapper      JSON 序列化器
     */
    public GatewayAuthSupport(
            GatewaySessionPort sessions,
            GatewaySessionProperties sessionProperties,
            GatewayProperties routes,
            ObjectMapper objectMapper
    ) {
        this.sessions = sessions;
        this.sessionProperties = sessionProperties;
        this.routes = routes;
        this.objectMapper = objectMapper;
    }

    /**
     * 路径是否在放行白名单内（登录与探活）。
     *
     * @param path 请求路径
     * @return 放行返回 true
     */
    public boolean isWhitelisted(String path) {
        if (path == null) {
            return false;
        }
        String loginPath = routes.resolvedGatewayApiPath() + "/sessions";
        return path.equals(loginPath)
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.equals("/error");
    }

    /**
     * 从请求头提取会话 token：优先 {@code Authorization: Bearer}，其次配置的自定义头。
     *
     * @param request 请求
     * @return token，缺失时为空串
     */
    public String extractToken(ServerHttpRequest request) {
        List<String> authorization = request.getHeaders().get(GatewayAttributes.AUTHORIZATION_HEADER);
        if (authorization != null) {
            for (String value : authorization) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (trimmed.regionMatches(true, 0, GatewayAttributes.BEARER_PREFIX, 0,
                        GatewayAttributes.BEARER_PREFIX.length())) {
                    String token = trimmed.substring(GatewayAttributes.BEARER_PREFIX.length()).trim();
                    if (!token.isEmpty()) {
                        return token;
                    }
                }
            }
        }
        String custom = request.getHeaders().getFirst(sessionProperties.resolvedTokenName());
        return custom == null ? "" : custom.trim();
    }

    /**
     * 校验会话。
     *
     * @param token 网关联会话 token
     * @return 会话；不存在或已过期时 {@link Mono#empty()}
     */
    public Mono<GatewaySession> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return sessions.find(token)
                .filter(session -> !session.expiredAt(Instant.now().getEpochSecond()));
    }

    /**
     * 登记调用者身份，并把上游凭据换成平台 token。
     *
     * <p><b>只改请求、不碰响应</b>：早期实现还在这里往响应头写会话掩码，结果代理路由在
     * 真实 Netty 下写响应时抛 {@code UnsupportedOperationException: setContentLength}
     * （现象是「平台侧 200、客户端连接被关闭」，实测踩过）。会话不回显响应头是更简单的选择。</p>
     *
     * @param exchange 交换对象
     * @param token    网关联会话 token
     * @param session  会话内容
     * @return 已换发凭据的交换对象
     */
    /**
     * 登记调用者身份，并把上游凭据换成平台 token（就地改请求头，不派生交换对象）。
     *
     * <p><b>为什么用就地改头而不是 {@code exchange.mutate()}</b>：代理路由最终要把上游响应
     * 写出去，而 mutate 派生出的交换对象在真实 Netty 下响应是只读的
     * （{@code ReadOnlyHttpHeaders}），写出时会抛 {@code UnsupportedOperationException: setContentLength}，
     * 现象是「平台侧 200、客户端却收到连接被关闭」（实测踩过，日志只有一句
     * {@code Error finishing response. Closing connection}，极难定位）。
     * 就地改请求头既达成了「上游用平台 token」，又不影响响应。</p>
     *
     * <p>同时不写任何响应头：同样会碰到只读响应的问题。</p>
     *
     * @param exchange 交换对象
     * @param token    网关联会话 token
     * @param session  会话内容
     * @return 原交换对象（就地改头，便于调用点保持链式写法）
     */
    /**
     * 登记调用者身份。
     *
     * <p><b>不换发任何凭据、不改写请求、不写响应头</b>：网关会话 token 就是平台 token
     * （见 {@code GatewaySessionUseCase}），因此客户端带来的
     * {@code Authorization: Bearer <token>} 可以原样透传给平台。</p>
     *
     * <p>曾经的实现会改写请求头并写一个会话掩码响应头，结果是：改写请求只能靠
     * {@code exchange.mutate()}，而它派生出的交换对象在真实 Netty 下响应是只读的，
     * 代理写出上游响应时抛 {@code UnsupportedOperationException: setContentLength}，
     * 现象是「平台侧 200、客户端连接被关闭」（实测踩过，见实现日志）。</p>
     *
     * @param exchange 交换对象
     * @param token    网关联会话 token（= 平台 token）
     * @param session  会话内容
     * @return 原交换对象（便于调用点保持链式写法）
     */
    public ServerWebExchange authenticated(ServerWebExchange exchange, String token, GatewaySession session) {
        exchange.getAttributes().put(GatewayAttributes.ATTR_PRINCIPAL,
                SessionPrincipal.from(token, session));
        return exchange;
    }

    /**
     * 代理路由专用：登记身份 + 换发上游凭据，<b>返回新的交换对象</b>。
     *
     * @param exchange 交换对象
     * @param token    网关联会话 token
     * @param session  会话内容
     * @return 已换发凭据的交换对象
     */
    public ServerWebExchange registerPrincipal(ServerWebExchange exchange, String token, GatewaySession session) {
        return authenticated(exchange, token, session);
    }

    /**
     * 写 401 统一信封。
     *
     * @param exchange 交换对象
     * @param reason   服务端日志说明（不对外透出）
     * @return 响应完成信号
     */
    public Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        String traceId = String.valueOf(
                exchange.getAttributes().getOrDefault(GatewayAttributes.ATTR_TRACE_ID, ""));
        ApiResponse<Void> body = ApiResponse.error(GatewayErrorCode.UNAUTHORIZED, traceId);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(serialize(body));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 读取当前请求身份。
     *
     * @param exchange 交换对象
     * @return 身份；未经鉴权时为 null
     */
    public SessionPrincipal principal(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(GatewayAttributes.ATTR_PRINCIPAL);
        return value instanceof SessionPrincipal principal ? principal : null;
    }

    /** 序列化失败时给出与信封同形状的最小 JSON，保证响应仍可解析。 */
    private byte[] serialize(ApiResponse<Void> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return ("{\"code\":\"" + body.code() + "\",\"message\":\"" + body.message()
                    + "\",\"data\":null,\"traceId\":\"" + body.traceId() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    /** 会话 token 掩码（响应头里不出现完整凭据）。 */
    static String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 8) + "***";
    }
}
