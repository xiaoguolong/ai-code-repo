package com.aicode.gateway.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * 平台服务 Feign 客户端（Week 19）。
 *
 * <p>URL 来自配置 {@code gateway.routes.platform-uri}（不引注册中心，见 Spec ADR）。</p>
 *
 * <p>链路透传：每个方法都显式声明 {@code X-Trace-Id} 头参数，由适配器从当前请求属性取值传入。
 * 之所以显式传参而不是在拦截器里读上下文：响应式链路上「当前请求」不是 ThreadLocal，
 * 拦截器里读不到可靠值；显式参数是唯一不靠猜的做法。</p>
 *
 * <p>认证：平台 sa-token 配的是 {@code token-name=Authorization} + {@code token-prefix=Bearer}，
 * 因此这里的 {@code authorization} 参数必须传<b>完整头值</b>（含 {@code Bearer } 前缀）。</p>
 */
@FeignClient(
        name = "platform",
        url = "${gateway.routes.platform-uri:http://localhost:8084}",
        configuration = PlatformFeignConfiguration.class
)
public interface PlatformFeignClient {

    /**
     * 平台登录。
     *
     * @param request 登录请求
     * @return 统一信封
     */
    @PostMapping("/api/v1/platform/auth/login")
    FeignEnvelope<FeignLoginPayload> login(@RequestBody FeignLoginRequest request);

    /**
     * 平台登出。
     *
     * @param authorization 完整 Authorization 头值（{@code Bearer <平台 token>}）
     * @param traceId       链路 ID
     * @return 统一信封
     */
    @PostMapping("/api/v1/platform/auth/logout")
    FeignEnvelope<Void> logout(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Trace-Id") String traceId);

    /**
     * 读取 Agent 目录。
     *
     * @param authorization 完整 Authorization 头值
     * @param traceId       链路 ID
     * @return 统一信封（data 为 Agent 列表）
     */
    @GetMapping("/api/v1/platform/agents")
    FeignEnvelope<List<FeignAgentPayload>> listAgents(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Trace-Id") String traceId);

    /**
     * 触发 Agent 执行。
     *
     * @param authorization 完整 Authorization 头值
     * @param traceId       链路 ID
     * @param agentKey      Agent 标识
     * @param body          Run 入参（平台契约为 {@code {"input": {...}}}）
     * @return 统一信封（data 为执行记录）
     */
    @PostMapping("/api/v1/platform/agents/{agentKey}/runs")
    FeignEnvelope<Map<String, Object>> runAgent(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Trace-Id") String traceId,
            @PathVariable("agentKey") String agentKey,
            @RequestBody Map<String, Object> body);
}
