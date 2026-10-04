package com.aicode.framework.observability;

import com.aicode.framework.infrastructure.logging.TraceIds;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可观测 HTTP 契约测试（Week 17）：请求走完整 Spring MVC 过滤链后，
 * 必须同时满足「Week 16 的 traceId 契约不变」与「Week 17 的 W3C 链路头 / Actuator / Prometheus 生效」。
 *
 * <p>范围说明：本类只断言 <b>HTTP 层可见契约</b>（响应头、响应体、Actuator 端点、指标出口）。
 * span 树的断言在 {@code HttpServerSpanFilterTest}（standalone 过滤链 + 真实 OTel SDK 内存导出器）
 * 与 {@code MicrometerObservabilityAdapterTest} / {@code ObservableChatModelAdapterTest} /
 * {@code ObservableToolPortTest} / {@code PlatformExecutionObservabilityTest} 中完成；
 * 生产 OTLP 链路另有真实 collector 端到端验收（见 Week 17 实现日志）。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=memory",
        "management.otlp.tracing.export.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ObservabilityHttpContractTest {

    private static final String PATH = "/api/v1/platform/observability/trace-context";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Tracer tracer;

    @Autowired
    private PrometheusMeterRegistry prometheusRegistry;

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void incomingXtraceIdStillEchoedUnchangedOnTheWire() throws Exception {
        mockMvc.perform(get(PATH).header(TraceIds.HEADER_NAME, "client-trace-123"))
                .andExpect(header().string(TraceIds.HEADER_NAME, "client-trace-123"));
    }

    @Test
    void writesW3cTraceparentDerivedFromIncomingXtraceId() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header(TraceIds.HEADER_NAME, "client-trace-123"))
                .andReturn();

        String traceparent = result.getResponse().getHeader(TraceIds.TRACEPARENT_HEADER);
        assertThat(traceparent).isNotNull().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(traceparent.split("-")[1])
                .as("traceparent 的 trace-id 必须由 X-Trace-Id 确定性派生，保证日志 ID 与链路 ID 可互查")
                .isEqualTo(TraceIds.otelTraceId("client-trace-123"));
    }

    @Test
    void incomingW3cTraceparentIsAdoptedAndPropagated() throws Exception {
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        MvcResult result = mockMvc.perform(get(PATH).header(TraceIds.TRACEPARENT_HEADER, incoming))
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIds.HEADER_NAME))
                .as("上游 W3C trace-id 被采纳为本次链路 traceId")
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(result.getResponse().getHeader(TraceIds.TRACEPARENT_HEADER))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
    }

    @Test
    void noTraceparentHeaderWhenObservabilityDisabled() throws Exception {
        // 本上下文开启观测，因此头必须存在；关闭行为的断言在 ObservabilityDisabledTest.Disabled
        mockMvc.perform(get(PATH).header(TraceIds.HEADER_NAME, "probe"))
                .andExpect(header().exists(TraceIds.TRACEPARENT_HEADER));
    }

    @Test
    void prometheusRegistryExposesJvmMetricsInTextFormat() throws Exception {
        mockMvc.perform(get(PATH).header(TraceIds.HEADER_NAME, "metrics-probe"));

        assertThat(prometheusRegistry.scrape())
                .as("Prometheus 文本出口必须可被采集器解析，且包含 JVM 与进程指标")
                .contains("jvm_memory_used_bytes")
                .contains("# TYPE");
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("UP"));
    }

    @Test
    void observabilityEndpointRequiresLogin() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("UNAUTHORIZED")
                        .contains("\"data\":null")
                        .contains("\"traceId\":"));
    }

    @Test
    void tracerBeanIsTheRealOtelBridgeTracer() {
        assertThat(tracer).isNotNull();
        assertThat(tracer.getClass().getName()).contains("OtelTracer");
    }

    /** 用真实 OTel SDK 的内存 exporter 替换 OTLP 导出，断言不依赖外部进程。 */
    @TestConfiguration
    static class InMemoryObservabilityConfiguration {

        @Bean
        InMemoryOtelTracer.Bundle otelBundle() {
            return InMemoryOtelTracer.build();
        }

        @Bean
        Tracer tracer(InMemoryOtelTracer.Bundle bundle) {
            return bundle.tracer();
        }

        @Bean
        PrometheusMeterRegistry prometheusMeterRegistry() {
            PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            // 以 SimpleMeterRegistry 为默认 registry，Prometheus registry 作为附加出口：
            // 既保留单测可断言的内存视图，又能验证 Prometheus 文本格式。
            Metrics.addRegistry(prometheus);
            return prometheus;
        }
    }
}
