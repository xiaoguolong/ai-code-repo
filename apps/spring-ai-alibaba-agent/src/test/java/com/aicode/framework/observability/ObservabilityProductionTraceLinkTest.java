package com.aicode.framework.observability;

import com.aicode.framework.infrastructure.logging.TraceIds;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产链路单源对齐测试（Week 17）。
 *
 * <p>验证「框架自动 HTTP 观测采纳我们注入的 traceparent」这条生产路径：请求只带自研
 * {@code X-Trace-Id}，链路后端最终记录的 traceId 必须等于 {@code TraceIds.otelTraceId(X-Trace-Id)}，
 * 且响应头 {@code traceparent} 的 trace-id 一致。这样「客户端串联键 ↔ 日志/审计/链路 traceId」
 * 才可互推。</p>
 *
 * <p>用真实端口（{@link TestRestTemplate}）而非 MockMvc：后者不会执行框架的
 * HTTP 观测过滤器，无法覆盖本路径（实测结论，见 Week 17 实现日志第 12 节）。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.openai.api-key=test-key",
                "auth.password-salt=test-salt",
                "platform.persistence.mode=memory",
                "platform.security.enforce-direct-runs=false",
                "management.otlp.tracing.export.enabled=false"
        })
@ActiveProfiles("test")
class ObservabilityProductionTraceLinkTest {

    private static final String PATH = "/api/v1/platform/observability/trace-context";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Tracer tracer;

    @Test
    void traceparentTraceIdEqualsOtelDerivationOfXtraceId() {
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();

        restTemplate.execute(url(PATH), HttpMethod.GET,
                request -> request.getHeaders().set(TraceIds.HEADER_NAME, "client-trace-123"),
                response -> {
                    headers.set(response.getHeaders());
                    return null;
                });

        HttpHeaders responseHeaders = headers.get();
        assertThat(responseHeaders).isNotNull();
        assertThat(responseHeaders.getFirst(TraceIds.HEADER_NAME)).isEqualTo("client-trace-123");

        String traceparent = responseHeaders.getFirst(TraceIds.TRACEPARENT_HEADER);
        assertThat(traceparent).isNotNull().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(traceparent.split("-")[1])
                .as("框架观测必须采纳注入的 traceparent，使链路 traceId = MD5(X-Trace-Id)")
                .isEqualTo(TraceIds.otelTraceId("client-trace-123"));
    }

    @Test
    void upstreamTraceparentIsAdoptedEndToEnd() {
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        String upstream = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        restTemplate.execute(url(PATH), HttpMethod.GET,
                request -> request.getHeaders().set(TraceIds.TRACEPARENT_HEADER, upstream),
                response -> {
                    headers.set(response.getHeaders());
                    return null;
                });

        HttpHeaders responseHeaders = headers.get();
        assertThat(responseHeaders).isNotNull();
        assertThat(responseHeaders.getFirst(TraceIds.HEADER_NAME))
                .as("上游 W3C trace-id 被采纳为本次链路 traceId")
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(responseHeaders.getFirst(TraceIds.TRACEPARENT_HEADER))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
    }

    @Test
    void tracerIsRealOtelBridgeTracer() {
        assertThat(tracer).isNotNull();
        assertThat(tracer.getClass().getName()).contains("OtelTracer");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 用内存 exporter 替换 OTLP 导出，避免单测打网络。 */
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
    }
}
