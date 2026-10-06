package com.aicode.gateway.infrastructure.feign;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.PlatformLogin;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.support.StubFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台出站端口 Feign 实现测试（Week 19）。
 *
 * <p>用假 Feign 客户端（而非真实 HTTP）覆盖三件事：请求头带上 Authorization 与 X-Trace-Id、
 * 上游业务码透传（4xx）、上游 5xx / 网络异常收敛为 502。真实 HTTP 形态由真机验收与部署文档覆盖。</p>
 */
class FeignPlatformClientAdapterTest {

    private StubFeignClient client;
    private FeignPlatformClientAdapter adapter;
    private GatewayProperties routes;

    @BeforeEach
    void setUp() {
        client = new StubFeignClient();
        routes = new GatewayProperties("http://platform:8084/", null, null);
        adapter = new FeignPlatformClientAdapter(client, routes, new ObjectMapper());
    }

    @Test
    @DisplayName("登录成功：映射为领域模型，且响应里不出现平台 token 之外的内部信息")
    void loginMapsPayload() {
        client.loginEnvelope = new FeignEnvelope<>("SUCCESS", "OK",
                new FeignLoginPayload("platform-token", 9L, "admin", "ADMIN"), "trace-1");

        StepVerifier.create(adapter.login("admin", "pwd"))
                .assertNext(login -> {
                    assertThat(login.platformToken()).isEqualTo("platform-token");
                    assertThat(login.userId()).isEqualTo(9L);
                    assertThat(login.username()).isEqualTo("admin");
                    assertThat(login.roleKey()).isEqualTo("ADMIN");
                })
                .verifyComplete();
        assertThat(client.lastLoginRequest.username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("登录失败（业务码非 SUCCESS）：抛 PlatformBusinessException")
    void loginRejectsNonSuccessCode() {
        client.loginEnvelope = new FeignEnvelope<>("UNAUTHORIZED", "账号或密码错误", null, "trace-1");

        StepVerifier.create(adapter.login("admin", "bad"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(PlatformBusinessException.class);
                    assertThat(((PlatformBusinessException) error).upstreamStatus()).isEqualTo(401);
                })
                .verify();
    }

    @Test
    @DisplayName("目录查询：透传 Bearer 与 X-Trace-Id，映射为领域模型")
    void listAgentsPassesHeaders() {
        client.agentsEnvelope = new FeignEnvelope<>("SUCCESS", "OK",
                List.of(new FeignAgentPayload("medical-assistant", "医疗助手", "desc",
                        "MEDICAL_ASSISTANT", true, "deepseek-v4-pro", 5, "2026-01-01T00:00:00Z")),
                "trace-2");

        StepVerifier.create(adapter.listAgents("platform-token", "trace-2"))
                .assertNext(agents -> {
                    assertThat(agents).hasSize(1);
                    AgentSummary summary = agents.get(0);
                    assertThat(summary.agentKey()).isEqualTo("medical-assistant");
                    assertThat(summary.agentType()).isEqualTo("MEDICAL_ASSISTANT");
                    assertThat(summary.model()).isEqualTo("deepseek-v4-pro");
                    assertThat(summary.maxIterations()).isEqualTo(5);
                })
                .verifyComplete();

        assertThat(client.lastAuthorization).isEqualTo("Bearer platform-token");
        assertThat(client.lastTraceId).isEqualTo("trace-2");
    }

    @Test
    @DisplayName("目录为空：返回空列表而不是 null")
    void emptyCatalogReturnsEmptyList() {
        client.agentsEnvelope = new FeignEnvelope<>("SUCCESS", "OK", null, "trace-2");
        StepVerifier.create(adapter.listAgents("t", "trace-2"))
                .assertNext(agents -> assertThat(agents).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("执行：请求体为 {\"input\":{...}}，结果原样透传")
    void runAgentWrapsInput() {
        client.runEnvelope = new FeignEnvelope<>("SUCCESS", "OK",
                Map.of("executionId", "exec-9", "status", "COMPLETED"), "trace-3");

        StepVerifier.create(adapter.runAgent("platform-token", "trace-3", "medical-assistant",
                        Map.of("patientId", "P001")))
                .assertNext(result -> assertThat(result).containsEntry("executionId", "exec-9"))
                .verifyComplete();

        assertThat(client.lastBody).containsKey("input");
        assertThat(client.lastAgentKey).isEqualTo("medical-assistant");
    }

    @Test
    @DisplayName("上游 4xx：解析报文里的业务码并透传（401 → UNAUTHORIZED）")
    void upstream4xxIsPropagatedWithCode() {
        client.listFailure = feignException(401,
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"未登录\",\"data\":null,\"traceId\":\"t\"}");

        StepVerifier.create(adapter.listAgents("t", "trace"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(PlatformBusinessException.class);
                    PlatformBusinessException business = (PlatformBusinessException) error;
                    assertThat(business.upstreamStatus()).isEqualTo(401);
                    assertThat(business.upstreamCode()).isEqualTo("UNAUTHORIZED");
                    assertThat(business.errorCode()).isEqualTo(GatewayErrorCode.UNAUTHORIZED);
                })
                .verify();
    }

    @Test
    @DisplayName("上游 4xx 报文无法解析：状态码仍透传，业务码为空")
    void upstream4xxWithoutParsableBody() {
        client.listFailure = feignException(400, "not-json");

        StepVerifier.create(adapter.listAgents("t", "trace"))
                .expectErrorSatisfies(error -> {
                    PlatformBusinessException business = (PlatformBusinessException) error;
                    assertThat(business.upstreamStatus()).isEqualTo(400);
                    assertThat(business.upstreamCode()).isEmpty();
                    assertThat(business.errorCode()).isEqualTo(GatewayErrorCode.VALIDATION_ERROR);
                })
                .verify();
    }

    @Test
    @DisplayName("上游 5xx：收敛为 GATEWAY_UPSTREAM_ERROR（不把 500 透给客户端）")
    void upstream5xxBecomesBadGateway() {
        client.runFailure = feignException(503, "{\"code\":\"INTERNAL_ERROR\"}");

        StepVerifier.create(adapter.runAgent("t", "trace", "agent", Map.of()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GatewayUpstreamException.class);
                    assertThat(((GatewayUpstreamException) error).errorCode())
                            .isEqualTo(GatewayErrorCode.GATEWAY_UPSTREAM_ERROR);
                })
                .verify();
    }

    @Test
    @DisplayName("网络异常（非 FeignException）：收敛为 502，原因只进日志")
    void networkFailureBecomesBadGateway() {
        client.runFailure = new RuntimeException("connect timed out");

        StepVerifier.create(adapter.runAgent("t", "trace", "agent", Map.of()))
                .expectErrorSatisfies(error -> {
                    GatewayUpstreamException upstream = (GatewayUpstreamException) error;
                    assertThat(upstream.errorCode()).isEqualTo(GatewayErrorCode.GATEWAY_UPSTREAM_ERROR);
                    assertThat(upstream.getMessage()).doesNotContain("stack");
                })
                .verify();
    }

    @Test
    @DisplayName("基础地址来自配置（自检展示与实际请求地址同源）")
    void baseUriComesFromConfiguration() {
        assertThat(adapter.baseUri()).isEqualTo("http://platform:8084");
        assertThat(routes.resolvedPlatformUri()).isEqualTo("http://platform:8084");
    }

    /** 构造 FeignException（用真实 Response 而不是 mock）。 */
    private static FeignException feignException(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/platform/agents",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("reason")
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("PlatformFeignClient#listAgents", response);
    }
}
