package com.aicode.framework.observability.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 监控概览 HTTP 契约测试（Week 20）。
 *
 * <p>本类把外部依赖全部指向空地址（Prometheus/Grafana/网关指标都不探测），验证的是
 * <b>协议层</b>：登录强校验、403 语义、统一信封、traceId 回显、以及响应中没有密钥。</p>
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)} 不是可有可无的：本类会启动完整应用上下文，
 * 其中 Micrometer/OTel 会把「上下文作用域事件 → MDC」的监听器注册到<b>全局</b>
 * OTel ContextStorage 上。上下文若被测试框架缓存到后续测试类，会让
 * {@code MicrometerObservabilityAdapterTest} 里「纯 OTel tracer 不写 MDC」的断言失败
 * （实测：单跑绿、与 Spring 测试同 JVM 跑红）。关闭上下文即可恢复隔离。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "monitoring.probe.timeout-ms=200",
        "monitoring.probe.cache-ttl-seconds=0",
        "monitoring.prometheus.base-url=",
        "monitoring.grafana.base-url=",
        "monitoring.targets.gateway-metrics-url=",
        "monitoring.links.skywalking-ui-url=",
        "monitoring.otel.trace-endpoint=",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformMonitoringHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("未登录 401，且仍带 traceId（Week 16 信封契约）")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/observability/monitoring-overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("管理员 200：六个组件 + 关联块 + 统一信封；响应头 traceId 与响应体一致")
    void adminGetsOverview() throws Exception {
        String token = login("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/platform/observability/monitoring-overview")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", "week20-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.components").isArray())
                .andExpect(jsonPath("$.data.components[0].key").value("otel"))
                .andExpect(jsonPath("$.data.components[0].status").value("UP"))
                .andExpect(jsonPath("$.data.allHealthy").exists())
                .andExpect(jsonPath("$.data.correlation.correlationTag").value("aicode.trace_id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().string("X-Trace-Id", "week20-overview"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").path("components")).hasSize(6);
        assertThat(body.path("data").path("components").toString().toLowerCase())
                .doesNotContain("secret", "password", "api-key");
        // 未配置的 Prometheus / Grafana / 网关指标必须是 DISABLED，而不是伪造的 UP
        assertThat(statusOf(body, "prometheus")).isEqualTo("DISABLED");
        assertThat(statusOf(body, "grafana")).isEqualTo("DISABLED");
        assertThat(statusOf(body, "gateway-metrics")).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("非管理员 403 FORBIDDEN（内部地址与拓扑只给管理员看）")
    void operatorGets403() throws Exception {
        String token = login("operator", "operator123");

        mockMvc.perform(get("/api/v1/platform/observability/monitoring-overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private String statusOf(JsonNode body, String key) {
        for (JsonNode node : body.path("data").path("components")) {
            if (key.equals(node.path("key").asText())) {
                return node.path("status").asText();
            }
        }
        throw new AssertionError("component not found: " + key);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }
}
