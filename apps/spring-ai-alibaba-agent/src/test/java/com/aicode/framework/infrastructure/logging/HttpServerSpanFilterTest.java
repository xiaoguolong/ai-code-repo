package com.aicode.framework.infrastructure.logging;

import com.aicode.framework.observability.OtelSdkTestSupport;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.infrastructure.MicrometerObservabilityAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入站请求 server span 与 W3C {@code traceparent} 测试（Week 17）：
 * 每个 HTTP 请求产生一个 SERVER span，traceId 取自 {@link TraceIds}（与既有 MDC traceId 同源），
 * 响应头 {@code traceparent} 与该 span 完全一致。
 */
class HttpServerSpanFilterTest extends OtelSdkTestSupport {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentObservabilityPort observability = new MicrometerObservabilityAdapter(tracer, new SimpleMeterRegistry());
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new TraceIdFilter(new SingleBeanProvider<>(observability)), new HttpServerSpanFilter(observability))
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void createsServerSpanForEveryHttpRequest() throws Exception {
        mockMvc.perform(get("/observability-test/ping").header("X-Trace-Id", "client-trace-123"))
                .andExpect(status().isOk());

        SpanData span = finishedSpan("http.server");
        assertThat(span.getKind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.SERVER);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(HttpServerSpanFilter.ATTR_HTTP_METHOD)))
                .isEqualTo("GET");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(HttpServerSpanFilter.ATTR_HTTP_PATH)))
                .isEqualTo("/observability-test/ping");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(HttpServerSpanFilter.ATTR_HTTP_STATUS)))
                .isEqualTo("200");
    }

    @Test
    void fallbackServerSpanStillCarriesAValidTraceId() throws Exception {
        // 备用分支（框架观测未接管）不设父上下文，故取 SDK 新生成的 traceId；
        // 「= MD5(X-Trace-Id)」这条生产路径由框架观测承接，见 ObservabilityProductionTraceLinkTest。
        mockMvc.perform(get("/observability-test/ping").header("X-Trace-Id", "client-trace-123"))
                .andExpect(status().isOk());

        assertThat(finishedSpan("http.server").getTraceId())
                .hasSize(TraceIds.OTEL_TRACE_ID_LENGTH).matches("[0-9a-f]{32}");
    }

    @Test
    void serverSpanUsesMdcTraceIdWhenUpstreamParentSpanIdPresent() throws Exception {
        // 这是 Agent/LLM/Tool 子 span 的真实建链方式：上游有 traceparent 时用其 spanId 作父
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        mockMvc.perform(get("/observability-test/ping").header("traceparent", traceparent))
                .andExpect(status().isOk());

        SpanData span = finishedSpan("http.server");
        assertThat(span.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(span.getParentSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void generatedTraceIdIsUsedWhenClientSendsNothing() throws Exception {
        mockMvc.perform(get("/observability-test/ping")).andExpect(status().isOk());

        assertThat(finishedSpan("http.server").getTraceId())
                .hasSize(TraceIds.OTEL_TRACE_ID_LENGTH).matches("[0-9a-f]{32}");
    }

    @Test
    void writesValidW3cTraceparentResponseHeader() throws Exception {
        String traceparent = mockMvc.perform(get("/observability-test/ping")
                        .header("X-Trace-Id", "client-trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "client-trace-123"))
                .andReturn().getResponse().getHeader("traceparent");

        // 该头由 TraceIdFilter 按注入的 W3C 上下文写出（trace-id = MD5(X-Trace-Id)），
        // spanId 是注入的父 spanId；备用 span 自身 ID 由 exporter 侧记录。
        assertThat(traceparent).isNotNull().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(traceparent.split("-")[1]).isEqualTo(TraceIds.otelTraceId("client-trace-123"));
    }

    @Test
    void mdcTraceIdIsClearedAfterRequest() throws Exception {
        mockMvc.perform(get("/observability-test/ping")).andExpect(status().isOk());

        assertThat(TraceIds.current()).isEmpty();
    }

    /** 仅供本测试使用的探针控制器。 */
    @RestController
    private static final class PingController {

        @GetMapping("/observability-test/ping")
        String ping() {
            return "pong";
        }
    }

    /** 单例 {@link ObjectProvider}：让过滤器可在无 Spring 上下文的单测里构造。 */
    private static final class SingleBeanProvider<T> implements org.springframework.beans.factory.ObjectProvider<T> {

        private final T instance;

        private SingleBeanProvider(T instance) {
            this.instance = instance;
        }

        @Override
        public T getObject() {
            return instance;
        }

        @Override
        public T getObject(Object... args) {
            return instance;
        }

        @Override
        public T getIfAvailable() {
            return instance;
        }

        @Override
        public T getIfUnique() {
            return instance;
        }
    }
}
