package com.aicode.gateway.controller;

import com.aicode.gateway.application.GatewayAgentUseCase;
import com.aicode.gateway.application.GatewayStatusUseCase;
import com.aicode.gateway.domain.exception.GatewayUnauthorizedException;
import com.aicode.gateway.domain.model.GatewayStatusView;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.dto.AgentResponse;
import com.aicode.gateway.dto.AgentRunResponse;
import com.aicode.gateway.dto.ApiResponse;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import com.aicode.gateway.infrastructure.security.GatewayAuthSupport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 网关 Agent 用例与自检 API（Week 19）。
 *
 * <ul>
 *   <li>{@code GET  /api/v1/gateway/status}：接入自检（路由 / 会话 / 链路 / Agent 挂载）；</li>
 *   <li>{@code GET  /api/v1/gateway/agents}：经 Feign 读平台 Agent 目录（Redis 缓存优先）；</li>
 *   <li>{@code POST /api/v1/gateway/agents/{agentKey}/runs}：经 Feign 触发平台执行。</li>
 * </ul>
 *
 * <p>注意：平台的透传路由挂在 {@code /api/v1/platform/**}（见 {@code application.yml} 的
 * {@code platform-proxy} 路由），与这里的 {@code /api/v1/gateway/**} 互不重叠；
 * 前者由 {@code GatewayAuthGlobalFilter} 鉴权，后者由 {@code GatewayAuthFilter} 鉴权。</p>
 */
@RestController
@RequestMapping(value = "/api/v1/gateway", produces = MediaType.APPLICATION_JSON_VALUE)
public class GatewayAgentController {

    private final GatewayStatusUseCase statusUseCase;
    private final GatewayAgentUseCase agentUseCase;
    private final GatewayAuthSupport authSupport;

    /**
     * @param statusUseCase 自检用例
     * @param agentUseCase  目录与执行用例
     * @param authSupport   鉴权支撑（读取当前身份）
     */
    public GatewayAgentController(
            GatewayStatusUseCase statusUseCase,
            GatewayAgentUseCase agentUseCase,
            GatewayAuthSupport authSupport
    ) {
        this.statusUseCase = statusUseCase;
        this.agentUseCase = agentUseCase;
        this.authSupport = authSupport;
    }

    /**
     * 网关接入自检。
     *
     * @param exchange 交换对象（链路上下文）
     * @return 自检视图（不含任何密钥）
     */
    @GetMapping("/status")
    public Mono<ApiResponse<GatewayStatusView>> status(ServerWebExchange exchange) {
        return statusUseCase.status(
                        attribute(exchange, GatewayAttributes.ATTR_TRACE_ID),
                        attribute(exchange, GatewayAttributes.ATTR_TRACEPARENT),
                        GatewayAttributes.TRACE_ID_HEADER,
                        attribute(exchange, GatewayAttributes.ATTR_INBOUND_TRACE_ID))
                .map(view -> ApiResponse.success(view, attribute(exchange, GatewayAttributes.ATTR_TRACE_ID)));
    }

    /**
     * 读取 Agent 目录（缓存优先）。
     *
     * @param refresh  true 表示跳过缓存强制回源
     * @param exchange 交换对象
     * @return 统一信封包装的 Agent 列表
     */
    @GetMapping("/agents")
    public Mono<ApiResponse<List<AgentResponse>>> listAgents(
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
            ServerWebExchange exchange) {
        SessionPrincipal principal = requirePrincipal(exchange);
        String traceId = attribute(exchange, GatewayAttributes.ATTR_TRACE_ID);
        return agentUseCase.listAgents(principal, traceId, refresh)
                .map(list -> ApiResponse.success(list.stream().map(AgentResponse::from).toList(), traceId));
    }

    /**
     * 触发一次 Agent 执行（经 Feign 调平台）。
     *
     * <p>请求体与平台契约一致：{@code {"input": {...}}}；为兼容直接传扁平入参的调用方，
     * 缺少 {@code input} 键时把整个请求体当作入参。入参语义必须在这里就归一到「平台的 input」，
     * 否则会被套成 {@code {"input":{"input":{...}}}} 传给模型（实测踩过）。</p>
     *
     * @param agentKey Agent 标识
     * @param body     请求体（{@code {"input": {...}}}）
     * @param exchange 交换对象
     * @return 统一信封包装的平台执行记录
     */
    @PostMapping("/agents/{agentKey}/runs")
    public Mono<ApiResponse<AgentRunResponse>> runAgent(
            @PathVariable("agentKey") String agentKey,
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        SessionPrincipal principal = requirePrincipal(exchange);
        String traceId = attribute(exchange, GatewayAttributes.ATTR_TRACE_ID);
        return agentUseCase.runAgent(principal, traceId, agentKey, resolveInput(body))
                .map(result -> ApiResponse.success(new AgentRunResponse(result), traceId));
    }

    /**
     * 把请求体归一为平台的 Run 入参。
     *
     * @param body 请求体，可为 null
     * @return 入参 Map（永不为 null）
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveInput(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Object nested = body.get("input");
        if (nested instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return body;
    }

    /** 读取当前身份；缺失时抛未授权（由统一异常处理落 401 信封）。 */
    private SessionPrincipal requirePrincipal(ServerWebExchange exchange) {
        SessionPrincipal principal = authSupport.principal(exchange);
        if (principal == null) {
            throw new GatewayUnauthorizedException("缺少调用者身份");
        }
        return principal;
    }

    /** 读取请求属性并转为字符串。 */
    private static String attribute(ServerWebExchange exchange, String key) {
        Object value = exchange.getAttributes().get(key);
        return value == null ? "" : value.toString();
    }
}
