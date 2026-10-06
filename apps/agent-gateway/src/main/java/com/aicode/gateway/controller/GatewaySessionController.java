package com.aicode.gateway.controller;

import com.aicode.gateway.application.GatewaySessionUseCase;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.dto.ApiResponse;
import com.aicode.gateway.dto.GatewayLoginRequest;
import com.aicode.gateway.dto.GatewayLoginResponse;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import com.aicode.gateway.infrastructure.security.GatewayAuthSupport;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关会话 API（Week 19）。
 *
 * <ul>
 *   <li>{@code POST /api/v1/gateway/sessions}：登录（经 Feign 调平台），返回网关联会话 token；</li>
 *   <li>{@code DELETE /api/v1/gateway/sessions/current}：登出（删网关会话）。</li>
 * </ul>
 *
 * <p>Controller 只做协议转换（规范 5.2）；登录裁决与权限判定分别在平台与用例层。</p>
 */
@RestController
@RequestMapping(value = "/api/v1/gateway/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
public class GatewaySessionController {

    private final GatewaySessionUseCase sessionUseCase;
    private final GatewaySessionProperties properties;
    private final GatewayAuthSupport authSupport;

    /**
     * @param sessionUseCase 会话用例
     * @param properties     会话配置（头名）
     * @param authSupport    鉴权支撑（读取当前身份）
     */
    public GatewaySessionController(
            GatewaySessionUseCase sessionUseCase,
            GatewaySessionProperties properties,
            GatewayAuthSupport authSupport
    ) {
        this.sessionUseCase = sessionUseCase;
        this.properties = properties;
        this.authSupport = authSupport;
    }

    /**
     * 登录并建立网关联会话。
     *
     * @param request  登录请求
     * @param exchange 交换对象（读取 traceId 写进信封）
     * @return 统一信封包装的会话信息（不含平台 token）
     */
    @PostMapping
    public Mono<ApiResponse<GatewayLoginResponse>> login(
            @Valid @RequestBody GatewayLoginRequest request, ServerWebExchange exchange) {
        return sessionUseCase.login(request.username(), request.password())
                .map(issued -> ApiResponse.success(
                        GatewayLoginResponse.from(issued, properties.resolvedTokenName()), traceId(exchange)));
    }

    /**
     * 登出：删除网关会话。
     *
     * @param exchange 交换对象（读取身份与 traceId）
     * @return 统一信封（data 为 null）
     */
    @DeleteMapping("/current")
    public Mono<ApiResponse<Void>> logout(ServerWebExchange exchange) {
        SessionPrincipal principal = authSupport.principal(exchange);
        if (principal == null) {
            return Mono.just(ApiResponse.error(
                    com.aicode.gateway.dto.GatewayErrorCode.UNAUTHORIZED, traceId(exchange)));
        }
        return sessionUseCase.logout(principal.sessionToken())
                .thenReturn(ApiResponse.success(null, traceId(exchange)));
    }

    /** 从请求属性取本次链路 ID。 */
    private static String traceId(ServerWebExchange exchange) {
        return String.valueOf(exchange.getAttributes().getOrDefault(GatewayAttributes.ATTR_TRACE_ID, ""));
    }
}
